// Global variables
let documentFile = null;
let stampFile = null;
let documentImage = null;
let stampImage = null;
let currentPosition = 'top-left';
let currentTransparency = 50;
let currentStampSize = 30;

// Initialize PDF.js
pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js';

// DOM elements
const documentInput = document.getElementById('documentInput');
const stampInput = document.getElementById('stampInput');
const documentUpload = document.getElementById('documentUpload');
const stampUpload = document.getElementById('stampUpload');
const documentInfo = document.getElementById('documentInfo');
const stampInfo = document.getElementById('stampInfo');
const documentName = document.getElementById('documentName');
const stampPreview = document.getElementById('stampPreview');
const previewCanvas = document.getElementById('previewCanvas');
const downloadBtn = document.getElementById('downloadBtn');
const loadingOverlay = document.getElementById('loadingOverlay');

// Initialize the application
document.addEventListener('DOMContentLoaded', function() {
    setupEventListeners();
    setupDragAndDrop();
    setupSliders();
    setupPositionButtons();
});

// Setup event listeners
function setupEventListeners() {
    documentInput.addEventListener('change', handleDocumentSelect);
    stampInput.addEventListener('change', handleStampSelect);
}

// Setup drag and drop functionality
function setupDragAndDrop() {
    // Document drag and drop
    documentUpload.addEventListener('dragover', (e) => {
        e.preventDefault();
        documentUpload.classList.add('dragover');
    });

    documentUpload.addEventListener('dragleave', () => {
        documentUpload.classList.remove('dragover');
    });

    documentUpload.addEventListener('drop', (e) => {
        e.preventDefault();
        documentUpload.classList.remove('dragover');
        const files = e.dataTransfer.files;
        if (files.length > 0) {
            handleDocumentFile(files[0]);
        }
    });

    // Stamp drag and drop
    stampUpload.addEventListener('dragover', (e) => {
        e.preventDefault();
        stampUpload.classList.add('dragover');
    });

    stampUpload.addEventListener('dragleave', () => {
        stampUpload.classList.remove('dragover');
    });

    stampUpload.addEventListener('drop', (e) => {
        e.preventDefault();
        stampUpload.classList.remove('dragover');
        const files = e.dataTransfer.files;
        if (files.length > 0) {
            handleStampFile(files[0]);
        }
    });
}

// Setup sliders
function setupSliders() {
    const transparencySlider = document.getElementById('transparency');
    const sizeSlider = document.getElementById('stampSize');
    const transparencyValue = document.getElementById('transparencyValue');
    const sizeValue = document.getElementById('sizeValue');

    transparencySlider.addEventListener('input', (e) => {
        currentTransparency = parseInt(e.target.value);
        transparencyValue.textContent = currentTransparency + '%';
        if (documentImage && stampImage) {
            updatePreview();
        }
    });

    sizeSlider.addEventListener('input', (e) => {
        currentStampSize = parseInt(e.target.value);
        sizeValue.textContent = currentStampSize + '%';
        if (documentImage && stampImage) {
            updatePreview();
        }
    });
}

// Setup position buttons
function setupPositionButtons() {
    const positionButtons = document.querySelectorAll('.position-btn');
    
    positionButtons.forEach(button => {
        button.addEventListener('click', () => {
            // Remove active class from all buttons
            positionButtons.forEach(btn => btn.classList.remove('active'));
            // Add active class to clicked button
            button.classList.add('active');
            currentPosition = button.dataset.position;
            
            if (documentImage && stampImage) {
                updatePreview();
            }
        });
    });
}

// Handle document selection
function handleDocumentSelect(e) {
    const file = e.target.files[0];
    if (file) {
        handleDocumentFile(file);
    }
}

// Handle stamp selection
function handleStampSelect(e) {
    const file = e.target.files[0];
    if (file) {
        handleStampFile(file);
    }
}

// Handle document file
async function handleDocumentFile(file) {
    showLoading();
    
    try {
        documentFile = file;
        documentName.textContent = file.name;
        
        if (file.type === 'application/pdf') {
            await loadPDFDocument(file);
        } else if (file.type.includes('word') || file.name.endsWith('.docx') || file.name.endsWith('.doc')) {
            await loadWordDocument(file);
        } else {
            throw new Error('Неподдерживаемый формат файла');
        }
        
        documentUpload.style.display = 'none';
        documentInfo.style.display = 'block';
        
        if (documentImage && stampImage) {
            updatePreview();
            downloadBtn.disabled = false;
        }
        
    } catch (error) {
        alert('Ошибка загрузки документа: ' + error.message);
    } finally {
        hideLoading();
    }
}

// Handle stamp file
function handleStampFile(file) {
    if (!file.type.startsWith('image/')) {
        alert('Пожалуйста, выберите изображение');
        return;
    }
    
    const reader = new FileReader();
    reader.onload = function(e) {
        stampFile = file;
        stampImage = new Image();
        stampImage.onload = function() {
            stampPreview.src = e.target.result;
            stampUpload.style.display = 'none';
            stampInfo.style.display = 'block';
            
            if (documentImage && stampImage) {
                updatePreview();
                downloadBtn.disabled = false;
            }
        };
        stampImage.src = e.target.result;
    };
    reader.readAsDataURL(file);
}

// Load PDF document
async function loadPDFDocument(file) {
    const arrayBuffer = await file.arrayBuffer();
    const pdf = await pdfjsLib.getDocument({data: arrayBuffer}).promise;
    const page = await pdf.getPage(1);
    
    const viewport = page.getViewport({scale: 1.5});
    const canvas = document.createElement('canvas');
    const context = canvas.getContext('2d');
    
    canvas.height = viewport.height;
    canvas.width = viewport.width;
    
    await page.render({
        canvasContext: context,
        viewport: viewport
    }).promise;
    
    documentImage = new Image();
    documentImage.onload = function() {
        updatePreview();
    };
    documentImage.src = canvas.toDataURL();
}

// Load Word document (simplified - creates a placeholder)
async function loadWordDocument(file) {
    // For simplicity, we'll create a placeholder image
    // In a real implementation, you'd use a library like mammoth.js or docx.js
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    
    canvas.width = 800;
    canvas.height = 1000;
    
    // White background
    ctx.fillStyle = 'white';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    
    // Text
    ctx.fillStyle = 'black';
    ctx.font = '24px Arial';
    ctx.fillText('Word Document', 50, 100);
    ctx.fillText('(Converted to PDF)', 50, 150);
    ctx.fillText('This is a placeholder for Word document content.', 50, 200);
    ctx.fillText('In a real implementation, the actual content would be displayed here.', 50, 250);
    
    documentImage = new Image();
    documentImage.onload = function() {
        updatePreview();
    };
    documentImage.src = canvas.toDataURL();
}

// Update preview
function updatePreview() {
    if (!documentImage || !stampImage) return;
    
    const ctx = previewCanvas.getContext('2d');
    
    // Set canvas size
    previewCanvas.width = documentImage.width;
    previewCanvas.height = documentImage.height;
    
    // Draw document
    ctx.drawImage(documentImage, 0, 0);
    
    // Calculate stamp position and size
    const stampWidth = (stampImage.width * currentStampSize) / 100;
    const stampHeight = (stampImage.height * currentStampSize) / 100;
    const margin = 50;
    
    let x, y;
    
    switch (currentPosition) {
        case 'top-left':
            x = margin;
            y = margin;
            break;
        case 'top-right':
            x = previewCanvas.width - stampWidth - margin;
            y = margin;
            break;
        case 'center':
            x = (previewCanvas.width - stampWidth) / 2;
            y = (previewCanvas.height - stampHeight) / 2;
            break;
        case 'bottom-left':
            x = margin;
            y = previewCanvas.height - stampHeight - margin;
            break;
        case 'bottom-right':
            x = previewCanvas.width - stampWidth - margin;
            y = previewCanvas.height - stampHeight - margin;
            break;
        default:
            x = margin;
            y = margin;
    }
    
    // Set transparency
    ctx.globalAlpha = (100 - currentTransparency) / 100;
    
    // Draw stamp
    ctx.drawImage(stampImage, x, y, stampWidth, stampHeight);
    
    // Reset transparency
    ctx.globalAlpha = 1;
}

// Download PDF
async function downloadPDF() {
    if (!documentImage || !stampImage) {
        alert('Пожалуйста, загрузите документ и печать');
        return;
    }
    
    showLoading();
    
    try {
        // Create a temporary canvas for the final image
        const tempCanvas = document.createElement('canvas');
        const tempCtx = tempCanvas.getContext('2d');
        
        tempCanvas.width = documentImage.width;
        tempCanvas.height = documentImage.height;
        
        // Draw document
        tempCtx.drawImage(documentImage, 0, 0);
        
        // Calculate stamp position and size
        const stampWidth = (stampImage.width * currentStampSize) / 100;
        const stampHeight = (stampImage.height * currentStampSize) / 100;
        const margin = 50;
        
        let x, y;
        
        switch (currentPosition) {
            case 'top-left':
                x = margin;
                y = margin;
                break;
            case 'top-right':
                x = tempCanvas.width - stampWidth - margin;
                y = margin;
                break;
            case 'center':
                x = (tempCanvas.width - stampWidth) / 2;
                y = (tempCanvas.height - stampHeight) / 2;
                break;
            case 'bottom-left':
                x = margin;
                y = tempCanvas.height - stampHeight - margin;
                break;
            case 'bottom-right':
                x = tempCanvas.width - stampWidth - margin;
                y = tempCanvas.height - stampHeight - margin;
                break;
            default:
                x = margin;
                y = margin;
        }
        
        // Set transparency
        tempCtx.globalAlpha = (100 - currentTransparency) / 100;
        
        // Draw stamp
        tempCtx.drawImage(stampImage, x, y, stampWidth, stampHeight);
        
        // Reset transparency
        tempCtx.globalAlpha = 1;
        
        // Convert to PDF using jsPDF
        const { jsPDF } = window.jspdf;
        const pdf = new jsPDF();
        
        // Get image data
        const imgData = tempCanvas.toDataURL('image/jpeg', 1.0);
        
        // Calculate PDF dimensions
        const pdfWidth = pdf.internal.pageSize.getWidth();
        const pdfHeight = pdf.internal.pageSize.getHeight();
        
        // Calculate image dimensions to fit in PDF
        const imgWidth = tempCanvas.width;
        const imgHeight = tempCanvas.height;
        const ratio = Math.min(pdfWidth / imgWidth, pdfHeight / imgHeight);
        
        const finalWidth = imgWidth * ratio;
        const finalHeight = imgHeight * ratio;
        
        // Center the image
        const xOffset = (pdfWidth - finalWidth) / 2;
        const yOffset = (pdfHeight - finalHeight) / 2;
        
        // Add image to PDF
        pdf.addImage(imgData, 'JPEG', xOffset, yOffset, finalWidth, finalHeight);
        
        // Save PDF
        const fileName = documentFile ? 
            documentFile.name.replace(/\.[^/.]+$/, '') + '_stamped.pdf' : 
            'stamped_document.pdf';
        
        pdf.save(fileName);
        
    } catch (error) {
        alert('Ошибка создания PDF: ' + error.message);
    } finally {
        hideLoading();
    }
}

// Remove document
function removeDocument() {
    documentFile = null;
    documentImage = null;
    documentUpload.style.display = 'block';
    documentInfo.style.display = 'none';
    documentInput.value = '';
    downloadBtn.disabled = true;
    
    // Clear preview
    const ctx = previewCanvas.getContext('2d');
    ctx.clearRect(0, 0, previewCanvas.width, previewCanvas.height);
}

// Remove stamp
function removeStamp() {
    stampFile = null;
    stampImage = null;
    stampUpload.style.display = 'block';
    stampInfo.style.display = 'none';
    stampInput.value = '';
    downloadBtn.disabled = true;
    
    // Clear preview
    const ctx = previewCanvas.getContext('2d');
    ctx.clearRect(0, 0, previewCanvas.width, previewCanvas.height);
}

// Show loading overlay
function showLoading() {
    loadingOverlay.style.display = 'flex';
}

// Hide loading overlay
function hideLoading() {
    loadingOverlay.style.display = 'none';
}

// Global functions for HTML onclick handlers
window.removeDocument = removeDocument;
window.removeStamp = removeStamp;
window.updatePreview = updatePreview;
window.downloadPDF = downloadPDF;