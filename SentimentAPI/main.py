
import os
import torch

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from transformers import AutoTokenizer, AutoModelForSequenceClassification

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

MODEL_DIR = os.path.join(BASE_DIR, "models", "xlm_roberta_final", "final")

DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")

LABELS = {
    0: "Negative",
    1: "Neutral",
    2: "Positive"
}



app = FastAPI(
    title="ComicWorm Sentiment API",
    description="Sentiment Analysis using XLM-RoBERTa",
    version="1.0.0"
)


print("Loading tokenizer...")

tokenizer = AutoTokenizer.from_pretrained(
    "FacebookAI/xlm-roberta-base"
)
print("Loading XLM-RoBERTa model...")

model = AutoModelForSequenceClassification.from_pretrained(MODEL_DIR)

model.to(DEVICE)
model.eval()

print("Model loaded successfully!")
print("Running on:", DEVICE)



class SentimentRequest(BaseModel):
    text: str


@app.get("/")
def home():
    return {
        "message": "ComicWorm Sentiment API is running",
        "device": str(DEVICE)
    }


@app.post("/predict")
def predict_sentiment(request: SentimentRequest):

    text = request.text.strip()

    if not text:
        raise HTTPException(
            status_code=400,
            detail="Text cannot be empty"
        )

    try:
        inputs = tokenizer(
            text,
            return_tensors="pt",
            truncation=True,
            padding=True,
            max_length=128
        )

        inputs = {
            key: value.to(DEVICE)
            for key, value in inputs.items()
        }

        with torch.no_grad():
            outputs = model(**inputs)

            probabilities = torch.softmax(
                outputs.logits,
                dim=-1
            )

            confidence, predicted_class = torch.max(
                probabilities,
                dim=-1
            )

        sentiment = LABELS[predicted_class.item()]

        return {
            "text": text,
            "sentiment": sentiment,
            "confidence": round(confidence.item(), 4)
        }

    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=str(e)
        )
