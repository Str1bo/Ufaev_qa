// Глобальные переменные
let uploadedDocument = null;
let uploadedStamp = null;

// Инициализация при загрузке страницы
document.addEventListener('DOMContentLoaded', function() {
    initializeEventListeners();
    updateOpacityValue();
});

// Инициализация обработчиков событий
function initializeEventListeners() {
    // Обработчики для загрузки файлов
    document.getElementById('documentInput').addEventListener('change', handleDocumentUpload);
    document.getElementById('stampInput').addEventListener('change', handleStampUpload);
    
    // Обработчики для настроек
    document.getElementById('opacity').addEventListener('input', updateOpacityValue);
    document.getElementById('xPosition').addEventListener('input', updatePreview);
    document.getElementById('yPosition').addEventListener('input', updatePreview);
    document.getElementById('positionMode').addEventListener('change', updatePositionMode);
}

// Обработка загрузки документа
async function handleDocumentUpload(event) {
    const file = event.target.files[0];
    if (!file) return;

    // Проверка типа файла
    const allowedTypes = ['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'application/msword'];
    if (!allowedTypes.includes(file.type)) {
        showNotification('Пожалуйста, выберите PDF или Word документ', 'error');
        return;
    }

    // Проверка размера файла (10MB)
    if (file.size > 10 * 1024 * 1024) {
        showNotification('Файл слишком большой. Максимальный размер: 10MB', 'error');
        return;
    }

    uploadedDocument = file;
    displayDocumentInfo(file);
    checkFilesUploaded();
}

// Обработка загрузки печати
async function handleStampUpload(event) {
    const file = event.target.files[0];
    if (!file) return;

    // Проверка типа файла
    const allowedTypes = ['image/jpeg', 'image/png'];
    if (!allowedTypes.includes(file.type)) {
        showNotification('Пожалуйста, выберите JPG или PNG изображение', 'error');
        return;
    }

    // Проверка размера файла (10MB)
    if (file.size > 10 * 1024 * 1024) {
        showNotification('Файл слишком большой. Максимальный размер: 10MB', 'error');
        return;
    }

    uploadedStamp = file;
    displayStampInfo(file);
    checkFilesUploaded();
}

// Отображение информации о документе
function displayDocumentInfo(file) {
    const documentInfo = document.getElementById('documentInfo');
    const documentUpload = document.getElementById('documentUpload');
    const documentName = document.getElementById('documentName');

    documentName.textContent = file.name;
    documentUpload.style.display = 'none';
    documentInfo.style.display = 'flex';
}

// Отображение информации о печати
function displayStampInfo(file) {
    const stampInfo = document.getElementById('stampInfo');
    const stampUpload = document.getElementById('stampUpload');
    const stampName = document.getElementById('stampName');
    const stampPreview = document.getElementById('stampPreview');

    stampName.textContent = file.name;
    
    // Создание превью изображения
    const reader = new FileReader();
    reader.onload = function(e) {
        stampPreview.src = e.target.result;
    };
    reader.readAsDataURL(file);

    stampUpload.style.display = 'none';
    stampInfo.style.display = 'flex';
}

// Удаление документа
function removeDocument() {
    uploadedDocument = null;
    document.getElementById('documentInput').value = '';
    document.getElementById('documentInfo').style.display = 'none';
    document.getElementById('documentUpload').style.display = 'flex';
    checkFilesUploaded();
}

// Удаление печати
function removeStamp() {
    uploadedStamp = null;
    document.getElementById('stampInput').value = '';
    document.getElementById('stampInfo').style.display = 'none';
    document.getElementById('stampUpload').style.display = 'flex';
    checkFilesUploaded();
}

// Проверка загрузки файлов
function checkFilesUploaded() {
    const settingsSection = document.getElementById('settingsSection');
    
    if (uploadedDocument && uploadedStamp) {
        settingsSection.style.display = 'block';
        updatePreview();
    } else {
        settingsSection.style.display = 'none';
    }
}

// Обновление значения прозрачности
function updateOpacityValue() {
    const opacitySlider = document.getElementById('opacity');
    const opacityValue = document.getElementById('opacityValue');
    const value = Math.round(opacitySlider.value * 100);
    opacityValue.textContent = value + '%';
    updatePreview();
}

// Обновление режима позиционирования
function updatePositionMode() {
    const positionMode = document.getElementById('positionMode');
    const customPosition = document.getElementById('customPosition');
    const xPosition = document.getElementById('xPosition');
    const yPosition = document.getElementById('yPosition');

    if (positionMode.value === 'custom') {
        customPosition.style.display = 'block';
    } else {
        customPosition.style.display = 'none';
        // Устанавливаем значения по умолчанию для предопределенных позиций
        if (positionMode.value === 'center') {
            xPosition.value = 200;
            yPosition.value = 150;
        } else if (positionMode.value === 'top-left') {
            xPosition.value = 50;
            yPosition.value = 250;
        } else if (positionMode.value === 'top-right') {
            xPosition.value = 350;
            yPosition.value = 250;
        } else if (positionMode.value === 'bottom-left') {
            xPosition.value = 50;
            yPosition.value = 50;
        } else if (positionMode.value === 'bottom-right') {
            xPosition.value = 350;
            yPosition.value = 50;
        }
    }
    updatePreview();
}

// Обновление предварительного просмотра
function updatePreview() {
    const previewStamp = document.getElementById('previewStamp');
    const xPosition = document.getElementById('xPosition');
    const yPosition = document.getElementById('yPosition');
    const opacity = document.getElementById('opacity');

    if (previewStamp) {
        previewStamp.style.left = xPosition.value + 'px';
        previewStamp.style.top = yPosition.value + 'px';
        previewStamp.style.opacity = opacity.value;
    }
}

// Сброс настроек
function resetSettings() {
    document.getElementById('positionMode').value = 'custom';
    document.getElementById('xPosition').value = 100;
    document.getElementById('yPosition').value = 100;
    document.getElementById('opacity').value = 0.8;
    document.getElementById('pageNumber').value = 1;
    
    updateOpacityValue();
    updatePositionMode();
    updatePreview();
}

// Обработка документа
async function processDocument() {
    if (!uploadedDocument || !uploadedStamp) {
        showNotification('Пожалуйста, загрузите документ и печать', 'error');
        return;
    }

    // Показываем экран загрузки
    showLoading(true);

    try {
        // Создаем FormData для загрузки файлов
        const formData = new FormData();
        formData.append('document', uploadedDocument);
        formData.append('stamp', uploadedStamp);

        // Загружаем файлы
        const uploadResponse = await fetch('/upload', {
            method: 'POST',
            body: formData
        });

        if (!uploadResponse.ok) {
            throw new Error('Ошибка загрузки файлов');
        }

        const uploadResult = await uploadResponse.json();

        if (!uploadResult.success) {
            throw new Error(uploadResult.error || 'Ошибка загрузки');
        }

        // Получаем параметры настроек
        const x = parseInt(document.getElementById('xPosition').value);
        const y = parseInt(document.getElementById('yPosition').value);
        const opacity = parseFloat(document.getElementById('opacity').value);
        const mode = document.getElementById('positionMode').value;
        const page = parseInt(document.getElementById('pageNumber').value);

        // Отправляем запрос на обработку
        const processResponse = await fetch('/process', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                documentPath: uploadResult.document.path,
                stampPath: uploadResult.stamp.path,
                x: x,
                y: y,
                opacity: opacity,
                mode: mode,
                page: page
            })
        });

        if (!processResponse.ok) {
            const errorData = await processResponse.json();
            throw new Error(errorData.error || 'Ошибка обработки документа');
        }

        // Скачиваем результат
        const blob = await processResponse.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'document_with_stamp.pdf';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);

        showNotification('Документ успешно обработан!', 'success');

    } catch (error) {
        console.error('Ошибка:', error);
        showNotification(error.message || 'Произошла ошибка при обработке документа', 'error');
    } finally {
        showLoading(false);
    }
}

// Показать/скрыть экран загрузки
function showLoading(show) {
    const mainContent = document.querySelector('.main-content');
    const loadingSection = document.getElementById('loadingSection');
    const settingsSection = document.getElementById('settingsSection');

    if (show) {
        mainContent.style.display = 'none';
        loadingSection.style.display = 'block';
    } else {
        mainContent.style.display = 'block';
        loadingSection.style.display = 'none';
    }
}

// Показать уведомление
function showNotification(message, type = 'info') {
    const notification = document.getElementById('notification');
    notification.textContent = message;
    notification.className = `notification ${type}`;
    notification.classList.add('show');

    // Автоматически скрыть через 5 секунд
    setTimeout(() => {
        notification.classList.remove('show');
    }, 5000);
}

// Drag and Drop функциональность
function setupDragAndDrop() {
    const uploadAreas = document.querySelectorAll('.upload-area');

    uploadAreas.forEach(area => {
        area.addEventListener('dragover', (e) => {
            e.preventDefault();
            area.style.borderColor = '#667eea';
            area.style.background = '#f0f2ff';
        });

        area.addEventListener('dragleave', (e) => {
            e.preventDefault();
            area.style.borderColor = '#e1e5e9';
            area.style.background = '#f8f9fa';
        });

        area.addEventListener('drop', (e) => {
            e.preventDefault();
            area.style.borderColor = '#e1e5e9';
            area.style.background = '#f8f9fa';

            const files = e.dataTransfer.files;
            if (files.length > 0) {
                const file = files[0];
                
                if (area.id === 'documentUpload') {
                    const input = document.getElementById('documentInput');
                    input.files = files;
                    handleDocumentUpload({ target: { files: files } });
                } else if (area.id === 'stampUpload') {
                    const input = document.getElementById('stampInput');
                    input.files = files;
                    handleStampUpload({ target: { files: files } });
                }
            }
        });
    });
}

// Инициализация drag and drop
setupDragAndDrop();