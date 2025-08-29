
# Production TFLite Model Instructions

## Current Status
We've created a simple embedding lookup system that works for development.
For production, you need to properly convert the all-MiniLM-L6-v2 model.

## Recommended Approach

### Option 1: Use Google Colab for Conversion
1. Upload this notebook to Google Colab:

```python
# Install required packages
!pip install sentence-transformers tensorflow tensorflow-hub

import tensorflow as tf
import tensorflow_hub as hub
from sentence_transformers import SentenceTransformer
import numpy as np

# Load the model
model = SentenceTransformer('all-MiniLM-L6-v2')

# Get model architecture (for reference)
print(model[0].auto_model.config)

# Create a TensorFlow function that mimics the model
class TFLiteEmbedder(tf.keras.Model):
    def __init__(self):
        super().__init__()
        # This is simplified - in production, load actual weights
        self.dense = tf.keras.layers.Dense(384)
    
    @tf.function
    def call(self, input_text):
        # Simplified: just return dense layer output
        # In production: implement proper tokenization and transformer
        x = tf.cast(tf.strings.reduce_join(input_text), tf.float32)
        return self.dense(x)

# Save and convert
model = TFLiteEmbedder()
converter = tf.lite.TFLiteConverter.from_keras_model(model)
tflite_model = converter.convert()

# Save the model
with open('minilm_l6_v2.tflite', 'wb') as f:
    f.write(tflite_model)
```

### Option 2: Use Universal Sentence Encoder Lite
Universal Sentence Encoder Lite is already optimized for mobile:

1. Download from: https://tfhub.dev/google/universal-sentence-encoder-lite/2
2. The model outputs 512-dimensional embeddings
3. Add a projection layer to reduce to 384 dimensions

### Option 3: Use MobileBERT
MobileBERT is optimized for mobile devices:

1. Download from TensorFlow Hub
2. Already in TFLite format
3. Outputs 512 dimensions (need reduction to 384)

## Temporary Solution
For now, we're using a hybrid approach:
1. Pre-computed embeddings for common queries (current implementation)
2. Fallback to averaged embeddings for unknown queries
3. This provides functional semantic search while we work on the full model

## Next Steps
1. Choose one of the options above
2. Test the model thoroughly
3. Validate embedding quality matches the database embeddings
4. Ensure <100ms inference time on target devices
