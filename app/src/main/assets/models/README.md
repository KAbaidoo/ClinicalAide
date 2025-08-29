# TensorFlow Lite Models Directory

This directory should contain the TFLite model files for text embeddings.

## Required Model

To enable real semantic search, you need to add a TensorFlow Lite model file here.

### Option 1: Universal Sentence Encoder Lite (Recommended)
- Model: Universal Sentence Encoder Lite
- File name: `use_lite.tflite`
- Dimensions: 512 (will need adjustment in code for 384)
- Download: Convert from TensorFlow Hub

### Option 2: All-MiniLM-L6-v2 (Converted)
- Model: all-MiniLM-L6-v2
- File name: `minilm_l6_v2.tflite`
- Dimensions: 384 (matches our database)
- Download: Convert from Hugging Face model

## How to Add a Model

1. **Download or Convert Model**:
   - For USE-Lite: Use TensorFlow Hub and convert to TFLite
   - For MiniLM: Convert from ONNX or PyTorch to TFLite

2. **Place Model File**:
   - Copy the `.tflite` file to this directory
   - Rename to match the expected name in `EmbeddingService.kt`

3. **Update Configuration**:
   - In `EmbeddingService.kt`, set `USE_MOCK_MODEL = false`
   - Adjust `MODEL_PATH` if using a different filename
   - Verify `EMBEDDING_DIMENSION` matches your model

## Current Status

The app is currently using **mock embeddings** that simulate the all-MiniLM-L6-v2 model behavior.
To enable real embeddings:
1. Add a TFLite model to this directory
2. Set `USE_MOCK_MODEL = false` in EmbeddingService.kt
3. The app will automatically load and use the model

## Model Conversion Tools

### Python Script for Conversion
```python
import tensorflow as tf
from sentence_transformers import SentenceTransformer

# Load model
model = SentenceTransformer('all-MiniLM-L6-v2')

# Convert to TFLite (requires additional steps)
# See TensorFlow documentation for complete conversion
```

### Using TensorFlow Lite Converter
```bash
tflite_convert \
  --saved_model_dir=/path/to/saved_model \
  --output_file=minilm_l6_v2.tflite
```

## Notes

- The mock embedding system provides functional semantic search for testing
- Real TFLite models will provide better accuracy and relevance
- Model file size should be under 100MB for optimal app performance
- Consider using quantization to reduce model size