#!/usr/bin/env python3

import numpy as np
import tensorflow as tf
from PIL import Image, ImageOps
from pathlib import Path

SEG_MODEL_FILE_PATH = "../../app/build/downloads/fairscan-segmentation-model.tflite"
DATASET_DIR = Path("../dataset")

INPUT_WIDTH = 256
INPUT_HEIGHT = 256

def get_resized_image(image_path):
    img = Image.open(image_path).convert("RGB")
    img = ImageOps.exif_transpose(img)
    img = img.convert("RGB").resize((INPUT_WIDTH, INPUT_HEIGHT), Image.BILINEAR)
    return img

def preprocess_image(img: Image.Image) -> np.ndarray:
    img_np = np.asarray(img).astype(np.float32)
    img_np = (img_np - 127.5) / 127.5  # Normalize to [-1, 1]
    return np.expand_dims(img_np, axis=0)

def postprocess_output(output: np.ndarray) -> np.ndarray:
    output = np.squeeze(output).astype(np.float32)  # Shape: (256, 256)
    output = np.clip(output, 0, 1)
    return output  # float32 array, values in [0,1]

def get_segmentation_mask(img):
    input_tensor = preprocess_image(img)
    interpreter.set_tensor(input_details['index'], input_tensor)
    interpreter.invoke()
    output_tensor = interpreter.get_tensor(output_details['index'])
    return postprocess_output(output_tensor)

interpreter = tf.lite.Interpreter(model_path=str(SEG_MODEL_FILE_PATH))
interpreter.allocate_tensors()
input_details = interpreter.get_input_details()[0]
output_details = interpreter.get_output_details()[0]

img_input_dir = DATASET_DIR / "images"
mask_input_dir = DATASET_DIR / "masks"

for image_path in sorted(img_input_dir.glob("*.jpg")):
    print(f"Generating mask for {image_path}")
    img = get_resized_image(image_path)
    img = ImageOps.exif_transpose(img)
    mask = get_segmentation_mask(img)
    mask_path = mask_input_dir / (image_path.stem + ".png")
    Image.fromarray((mask * 255).astype(np.uint8)).save(mask_path)
