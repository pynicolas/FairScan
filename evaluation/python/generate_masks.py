#!/usr/bin/env python3

import numpy as np
import tensorflow as tf
import cv2
from pathlib import Path

SEG_MODEL_FILE_PATH = "../../app/build/downloads/tflite/fairscan-segmentation-model.tflite"
DATASET_DIR = Path("../dataset")

INPUT_WIDTH = 256
INPUT_HEIGHT = 256

def get_resized_image(image_path):
    img = cv2.imread(str(image_path), cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError(f"Failed to load image: {image_path}")
    img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
    img = cv2.resize(
        img,
        (INPUT_WIDTH, INPUT_HEIGHT),
        interpolation=cv2.INTER_LINEAR
    )
    return img

def preprocess_image(img):
    img = img.astype(np.float32)
    img = img / 127.5 - 1.0
    return img[np.newaxis, ...]

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
    mask = get_segmentation_mask(img)
    mask_path = mask_input_dir / (image_path.stem + ".png")
    mask_uint8 = (mask * 255.0).round().astype(np.uint8)
    cv2.imwrite(str(mask_path), mask_uint8)
