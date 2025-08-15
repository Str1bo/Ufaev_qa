const express = require('express');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const cors = require('cors');
const { PDFDocument } = require('pdf-lib');
const mammoth = require('mammoth');
const sharp = require('sharp');

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(cors());
app.use(express.json());
app.use(express.static('public'));

// Создаем папки для загрузок если их нет
const uploadsDir = path.join(__dirname, 'uploads');
const tempDir = path.join(__dirname, 'temp');

if (!fs.existsSync(uploadsDir)) {
    fs.mkdirSync(uploadsDir, { recursive: true });
}
if (!fs.existsSync(tempDir)) {
    fs.mkdirSync(tempDir, { recursive: true });
}

// Настройка multer для загрузки файлов
const storage = multer.diskStorage({
    destination: function (req, file, cb) {
        cb(null, uploadsDir);
    },
    filename: function (req, file, cb) {
        const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
        cb(null, file.fieldname + '-' + uniqueSuffix + path.extname(file.originalname));
    }
});

const upload = multer({ 
    storage: storage,
    fileFilter: function (req, file, cb) {
        const allowedTypes = [
            'application/pdf',
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
            'application/msword',
            'image/jpeg',
            'image/png'
        ];
        
        if (allowedTypes.includes(file.mimetype)) {
            cb(null, true);
        } else {
            cb(new Error('Неподдерживаемый тип файла'), false);
        }
    },
    limits: {
        fileSize: 10 * 1024 * 1024 // 10MB
    }
});

// Маршруты
app.get('/', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// Загрузка документов и печати
app.post('/upload', upload.fields([
    { name: 'document', maxCount: 1 },
    { name: 'stamp', maxCount: 1 }
]), async (req, res) => {
    try {
        if (!req.files.document || !req.files.stamp) {
            return res.status(400).json({ error: 'Необходимо загрузить документ и печать' });
        }

        const documentFile = req.files.document[0];
        const stampFile = req.files.stamp[0];

        res.json({
            success: true,
            document: {
                filename: documentFile.filename,
                originalname: documentFile.originalname,
                path: documentFile.path
            },
            stamp: {
                filename: stampFile.filename,
                originalname: stampFile.originalname,
                path: stampFile.path
            }
        });
    } catch (error) {
        console.error('Ошибка загрузки:', error);
        res.status(500).json({ error: 'Ошибка при загрузке файлов' });
    }
});

// Обработка наложения печати
app.post('/process', async (req, res) => {
    try {
        const { 
            documentPath, 
            stampPath, 
            x, 
            y, 
            opacity, 
            mode, 
            page 
        } = req.body;

        const fullDocumentPath = path.join(uploadsDir, path.basename(documentPath));
        const fullStampPath = path.join(uploadsDir, path.basename(stampPath));

        // Проверяем существование файлов
        if (!fs.existsSync(fullDocumentPath) || !fs.existsSync(fullStampPath)) {
            return res.status(404).json({ error: 'Файлы не найдены' });
        }

        // Обрабатываем печать
        const processedStampPath = await processStamp(fullStampPath, opacity);
        
        // Накладываем печать на документ
        const resultPath = await applyStampToDocument(
            fullDocumentPath, 
            processedStampPath, 
            x, 
            y, 
            mode, 
            page
        );

        // Отправляем результат
        res.download(resultPath, 'document_with_stamp.pdf', (err) => {
            if (err) {
                console.error('Ошибка отправки файла:', err);
            }
            // Удаляем временные файлы
            setTimeout(() => {
                try {
                    fs.unlinkSync(processedStampPath);
                    fs.unlinkSync(resultPath);
                } catch (e) {
                    console.error('Ошибка удаления временных файлов:', e);
                }
            }, 5000);
        });

    } catch (error) {
        console.error('Ошибка обработки:', error);
        res.status(500).json({ error: 'Ошибка при обработке документа' });
    }
});

// Функция обработки печати
async function processStamp(stampPath, opacity) {
    const outputPath = path.join(tempDir, `processed_stamp_${Date.now()}.png`);
    
    await sharp(stampPath)
        .png()
        .composite([{
            input: Buffer.from([255, 255, 255, Math.round(255 * opacity)]),
            raw: { width: 1, height: 1, channels: 4 },
            tile: true,
            blend: 'multiply'
        }])
        .toFile(outputPath);
    
    return outputPath;
}

// Функция наложения печати на документ
async function applyStampToDocument(documentPath, stampPath, x, y, mode, page) {
    const outputPath = path.join(tempDir, `result_${Date.now()}.pdf`);
    
    // Читаем PDF документ
    const pdfBytes = fs.readFileSync(documentPath);
    const pdfDoc = await PDFDocument.load(pdfBytes);
    
    // Читаем печать
    const stampBytes = fs.readFileSync(stampPath);
    const stampImage = await pdfDoc.embedPng(stampBytes);
    
    const pages = pdfDoc.getPages();
    const targetPage = page ? Math.min(page - 1, pages.length - 1) : 0;
    const pdfPage = pages[targetPage];
    
    const { width, height } = pdfPage.getSize();
    const stampWidth = stampImage.width;
    const stampHeight = stampImage.height;
    
    // Вычисляем позицию
    let finalX = x;
    let finalY = height - y - stampHeight; // Инвертируем Y координату
    
    if (mode === 'center') {
        finalX = (width - stampWidth) / 2;
        finalY = (height - stampHeight) / 2;
    } else if (mode === 'top-left') {
        finalX = 50;
        finalY = height - stampHeight - 50;
    } else if (mode === 'top-right') {
        finalX = width - stampWidth - 50;
        finalY = height - stampHeight - 50;
    } else if (mode === 'bottom-left') {
        finalX = 50;
        finalY = 50;
    } else if (mode === 'bottom-right') {
        finalX = width - stampWidth - 50;
        finalY = 50;
    }
    
    // Накладываем печать
    pdfPage.drawImage(stampImage, {
        x: finalX,
        y: finalY,
        width: stampWidth,
        height: stampHeight,
    });
    
    // Сохраняем результат
    const modifiedPdfBytes = await pdfDoc.save();
    fs.writeFileSync(outputPath, modifiedPdfBytes);
    
    return outputPath;
}

// Обработка ошибок multer
app.use((error, req, res, next) => {
    if (error instanceof multer.MulterError) {
        if (error.code === 'LIMIT_FILE_SIZE') {
            return res.status(400).json({ error: 'Файл слишком большой (максимум 10MB)' });
        }
    }
    res.status(500).json({ error: error.message });
});

app.listen(PORT, () => {
    console.log(`Сервер запущен на порту ${PORT}`);
    console.log(`Откройте http://localhost:${PORT} в браузере`);
});