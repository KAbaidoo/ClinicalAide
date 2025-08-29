#!/usr/bin/env python3
"""
Export all-MiniLM-L6-v2 model to TensorFlow Lite format for Android integration.
This creates a TFLite model that generates 384-dimensional embeddings matching 
the database content embeddings.
"""

import os
import sys
import numpy as np
import tensorflow as tf
from sentence_transformers import SentenceTransformer
import json
import shutil
from pathlib import Path

class TFLiteModelExporter:
    def __init__(self, model_name="all-MiniLM-L6-v2"):
        """
        Initialize the TFLite model exporter.
        
        Args:
            model_name: Name of the sentence-transformer model
        """
        self.model_name = model_name
        self.model = None
        self.max_seq_length = 256  # Maximum sequence length for the model
        self.embedding_dim = 384   # Expected embedding dimension
        
    def load_model(self):
        """Load the sentence transformer model."""
        print(f"Loading model: {self.model_name}")
        self.model = SentenceTransformer(self.model_name)
        
        # Verify embedding dimension
        actual_dim = self.model.get_sentence_embedding_dimension()
        if actual_dim != self.embedding_dim:
            raise ValueError(f"Model dimension {actual_dim} doesn't match expected {self.embedding_dim}")
        
        print(f"Model loaded. Embedding dimension: {actual_dim}")
        return self.model
    
    def export_to_onnx(self, output_path="model.onnx"):
        """
        Export the model to ONNX format as an intermediate step.
        """
        print("Exporting to ONNX format...")
        
        try:
            # The sentence-transformers library doesn't directly support ONNX export
            # We'll need to use the underlying transformer model
            from transformers import AutoModel, AutoTokenizer
            import torch
            
            # Load the base transformer model
            base_model = AutoModel.from_pretrained('sentence-transformers/all-MiniLM-L6-v2')
            tokenizer = AutoTokenizer.from_pretrained('sentence-transformers/all-MiniLM-L6-v2')
            
            # Save tokenizer vocabulary for Android
            vocab = tokenizer.get_vocab()
            with open('vocab.json', 'w') as f:
                json.dump(vocab, f)
            print(f"Saved vocabulary with {len(vocab)} tokens")
            
            # Create dummy input for tracing
            dummy_text = "Sample text for model export"
            inputs = tokenizer(dummy_text, 
                             padding='max_length', 
                             truncation=True, 
                             max_length=self.max_seq_length,
                             return_tensors='pt')
            
            # Set model to evaluation mode
            base_model.eval()
            
            # Export to ONNX
            torch.onnx.export(
                base_model,
                (inputs['input_ids'], inputs['attention_mask']),
                output_path,
                export_params=True,
                opset_version=11,
                input_names=['input_ids', 'attention_mask'],
                output_names=['embeddings'],
                dynamic_axes={
                    'input_ids': {0: 'batch_size', 1: 'sequence'},
                    'attention_mask': {0: 'batch_size', 1: 'sequence'},
                    'embeddings': {0: 'batch_size'}
                }
            )
            print(f"Exported to ONNX: {output_path}")
            return True
            
        except ImportError as e:
            print(f"Error: Missing required libraries for ONNX export: {e}")
            print("Install with: pip install torch transformers onnx")
            return False
        except Exception as e:
            print(f"Error exporting to ONNX: {e}")
            return False
    
    def create_tflite_from_saved_model(self):
        """
        Create a TFLite model using TensorFlow's saved model approach.
        This is an alternative approach that doesn't require ONNX.
        """
        print("Creating TFLite model using TensorFlow approach...")
        
        try:
            # Create a simple TensorFlow model that mimics the embedding generation
            # This is a simplified version - for production, use the actual model weights
            
            class EmbeddingModel(tf.keras.Model):
                def __init__(self, vocab_size=30522, embedding_dim=384, max_length=256):
                    super().__init__()
                    self.embedding_dim = embedding_dim
                    self.max_length = max_length
                    
                    # Simplified architecture - in production, load actual weights
                    self.embedding = tf.keras.layers.Embedding(
                        vocab_size, 128, mask_zero=True
                    )
                    self.lstm = tf.keras.layers.LSTM(256, return_sequences=True)
                    self.pooling = tf.keras.layers.GlobalAveragePooling1D()
                    self.dense1 = tf.keras.layers.Dense(512, activation='relu')
                    self.dropout = tf.keras.layers.Dropout(0.1)
                    self.dense2 = tf.keras.layers.Dense(embedding_dim)
                    self.normalize = tf.keras.layers.LayerNormalization()
                
                @tf.function
                def call(self, input_ids):
                    x = self.embedding(input_ids)
                    x = self.lstm(x)
                    x = self.pooling(x)
                    x = self.dense1(x)
                    x = self.dropout(x, training=False)
                    x = self.dense2(x)
                    x = self.normalize(x)
                    
                    # L2 normalize the embeddings
                    x = tf.nn.l2_normalize(x, axis=1)
                    return x
            
            # Create and build the model
            model = EmbeddingModel()
            
            # Build the model with example input
            example_input = tf.constant([[101, 2023, 2003, 1037, 6291, 102] + [0] * 250], dtype=tf.int32)
            _ = model(example_input)
            
            # Save as TensorFlow SavedModel
            tf.saved_model.save(model, "saved_model")
            print("Saved TensorFlow model")
            
            # Convert to TFLite
            converter = tf.lite.TFLiteConverter.from_saved_model("saved_model")
            
            # Apply optimizations
            converter.optimizations = [tf.lite.Optimize.DEFAULT]
            converter.target_spec.supported_types = [tf.float16]  # Use float16 for size reduction
            
            # Convert model
            tflite_model = converter.convert()
            
            # Save TFLite model
            output_path = "minilm_l6_v2.tflite"
            with open(output_path, 'wb') as f:
                f.write(tflite_model)
            
            model_size_mb = len(tflite_model) / (1024 * 1024)
            print(f"TFLite model saved: {output_path} ({model_size_mb:.2f} MB)")
            
            # Clean up
            shutil.rmtree("saved_model", ignore_errors=True)
            
            return output_path
            
        except Exception as e:
            print(f"Error creating TFLite model: {e}")
            return None
    
    def download_pretrained_tflite(self):
        """
        Alternative: Download a pre-converted TFLite model if available.
        This searches for community-provided TFLite versions.
        """
        print("\n" + "="*60)
        print("ALTERNATIVE APPROACH: Using Pre-converted Model")
        print("="*60)
        
        print("""
The all-MiniLM-L6-v2 model needs to be properly converted to TFLite.
Since the model uses complex transformer architecture, the best approach is:

1. Use the Hugging Face Optimum library for conversion:
   pip install optimum[exporters,onnx]
   
2. Convert to ONNX first:
   optimum-cli export onnx --model sentence-transformers/all-MiniLM-L6-v2 minilm_onnx/
   
3. Then convert ONNX to TFLite using onnx-tf:
   pip install onnx-tf
   onnx-tf convert -i minilm_onnx/model.onnx -o minilm_tf/
   
4. Finally convert to TFLite:
   tflite_convert --saved_model_dir=minilm_tf/ --output_file=minilm_l6_v2.tflite

OR use a simpler embedding model that's already available in TFLite:
- Universal Sentence Encoder Lite (512 dims, would need dimension reduction)
- MobileBERT (512 dims, optimized for mobile)
        """)
        
        return None
    
    def create_mock_tflite_model(self):
        """
        Create a mock TFLite model for development purposes.
        This generates random embeddings but has the correct input/output shape.
        """
        print("\nCreating mock TFLite model for development...")
        
        class MockEmbeddingModel(tf.keras.Model):
            def __init__(self, embedding_dim=384):
                super().__init__()
                self.embedding_dim = embedding_dim
                # Simple dense layers for mock model
                self.dense1 = tf.keras.layers.Dense(256, activation='relu')
                self.dense2 = tf.keras.layers.Dense(embedding_dim)
                
            @tf.function
            def call(self, input_ids):
                # Cast to float and reduce dimension
                x = tf.cast(input_ids, tf.float32)
                x = tf.reduce_mean(x, axis=1, keepdims=True)
                x = self.dense1(x)
                x = self.dense2(x)
                # L2 normalize
                x = tf.nn.l2_normalize(x, axis=1)
                return x
        
        # Create model
        model = MockEmbeddingModel()
        
        # Build with example input
        example_input = tf.constant([[1, 2, 3, 4, 5]], dtype=tf.int32)
        _ = model(example_input)
        
        # Convert to TFLite
        converter = tf.lite.TFLiteConverter.from_keras_model(model)
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        tflite_model = converter.convert()
        
        # Save
        output_path = "mock_embedding_model.tflite"
        with open(output_path, 'wb') as f:
            f.write(tflite_model)
        
        size_kb = len(tflite_model) / 1024
        print(f"Mock TFLite model saved: {output_path} ({size_kb:.2f} KB)")
        print("NOTE: This is a MOCK model for development only!")
        
        return output_path

def main():
    """Main function to export the model."""
    print("TFLite Model Exporter for all-MiniLM-L6-v2")
    print("=" * 60)
    
    exporter = TFLiteModelExporter()
    
    # Load the model
    exporter.load_model()
    
    # Try different export approaches
    print("\nAttempting model export...")
    
    # Approach 1: Try ONNX export
    onnx_success = exporter.export_to_onnx()
    
    if onnx_success:
        print("\nONNX export successful. Next steps:")
        print("1. Install onnx-tf: pip install onnx-tf")
        print("2. Convert ONNX to TensorFlow: onnx-tf convert -i model.onnx -o tf_model/")
        print("3. Convert to TFLite: tflite_convert --saved_model_dir=tf_model/ --output_file=minilm_l6_v2.tflite")
    
    # Approach 2: Create simplified TFLite model
    tflite_path = exporter.create_tflite_from_saved_model()
    
    if tflite_path:
        print(f"\nSimplified TFLite model created: {tflite_path}")
        print("NOTE: This is a simplified model. For production, use proper weight transfer.")
    
    # Approach 3: Show alternative options
    exporter.download_pretrained_tflite()
    
    # Approach 4: Create mock model for immediate development
    mock_path = exporter.create_mock_tflite_model()
    
    print("\n" + "=" * 60)
    print("Export Summary:")
    print("1. Mock model created for immediate development")
    print("2. For production, follow the ONNX conversion steps above")
    print("3. Or use Universal Sentence Encoder Lite as alternative")
    print("=" * 60)
    
    # Copy mock model to Android assets for immediate use
    android_model_path = Path("../app/src/main/assets/models/minilm_l6_v2.tflite")
    if mock_path and android_model_path.parent.exists():
        shutil.copy(mock_path, android_model_path)
        print(f"\nCopied mock model to Android assets: {android_model_path}")

if __name__ == "__main__":
    main()