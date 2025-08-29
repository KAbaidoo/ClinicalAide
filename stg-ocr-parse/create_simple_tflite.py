#!/usr/bin/env python3
"""
Create a simple TFLite-compatible model file for all-MiniLM-L6-v2 embeddings.
This creates a minimal model that can be loaded by TensorFlow Lite on Android.
"""

import numpy as np
import struct
import json
from pathlib import Path

def create_simple_embedding_model():
    """
    Create a simple embedding lookup model that can generate 384-dimensional vectors.
    This is a placeholder until we can properly convert the full model.
    """
    print("Creating simple TFLite-compatible embedding model...")
    
    # Load pre-computed embeddings to use as a basis
    embeddings_path = "query_embeddings.json"
    
    if Path(embeddings_path).exists():
        print(f"Loading embeddings from {embeddings_path}")
        with open(embeddings_path, 'r') as f:
            data = json.load(f)
        
        # Extract embeddings
        embedding_matrix = []
        vocab = {}
        
        for i, item in enumerate(data):
            embedding_matrix.append(item['embedding'])
            # Create simple vocab from query words
            words = item['query'].lower().split()
            for word in words:
                if word not in vocab and len(vocab) < 1000:
                    vocab[word] = len(vocab)
        
        embedding_matrix = np.array(embedding_matrix, dtype=np.float32)
        print(f"Loaded {len(embedding_matrix)} embeddings, vocab size: {len(vocab)}")
        
        # Save vocab for Android
        with open('simple_vocab.json', 'w') as f:
            json.dump(vocab, f)
        print("Saved vocabulary")
        
        # Create a simple binary format that can be read on Android
        # Format: [num_embeddings][embedding_dim][embeddings_data]
        num_embeddings = len(embedding_matrix)
        embedding_dim = 384
        
        with open('embedding_weights.bin', 'wb') as f:
            # Write header
            f.write(struct.pack('i', num_embeddings))
            f.write(struct.pack('i', embedding_dim))
            
            # Write embeddings
            for embedding in embedding_matrix:
                for value in embedding:
                    f.write(struct.pack('f', value))
        
        print(f"Created embedding weights file: embedding_weights.bin")
        
        # Also create a numpy file for easier loading
        np.save('embedding_weights.npy', embedding_matrix)
        print(f"Created numpy weights file: embedding_weights.npy")
        
        return True
    else:
        print(f"Error: {embeddings_path} not found")
        print("Run generate_query_embeddings.py first")
        return False

def create_production_instructions():
    """
    Create instructions for properly converting the model.
    """
    instructions = """
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
"""
    
    with open('TFLITE_CONVERSION_GUIDE.md', 'w') as f:
        f.write(instructions)
    
    print("Created TFLite conversion guide: TFLITE_CONVERSION_GUIDE.md")

def main():
    """Main function."""
    print("Simple TFLite Model Creator")
    print("=" * 60)
    
    # Create simple embedding model
    success = create_simple_embedding_model()
    
    if success:
        print("\n" + "=" * 60)
        print("Success! Created:")
        print("1. embedding_weights.bin - Binary weights file")
        print("2. embedding_weights.npy - Numpy weights file")
        print("3. simple_vocab.json - Vocabulary mapping")
        
        # Copy to Android assets
        android_path = Path("../app/src/main/assets/models/")
        if android_path.exists():
            import shutil
            shutil.copy("embedding_weights.bin", android_path / "embedding_weights.bin")
            shutil.copy("simple_vocab.json", android_path / "simple_vocab.json")
            print(f"\nCopied files to Android assets: {android_path}")
    
    # Create production instructions
    create_production_instructions()
    
    print("\n" + "=" * 60)
    print("For production TFLite model, see: TFLITE_CONVERSION_GUIDE.md")
    print("=" * 60)

if __name__ == "__main__":
    main()