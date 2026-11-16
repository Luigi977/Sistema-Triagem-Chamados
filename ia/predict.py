"""Inferência CLI a partir do mesmo contrato da API."""
import argparse
from src.main import predict
from src.schemas import PredictionRequest

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('texto')
    args = parser.parse_args()
    print(predict(PredictionRequest(texto=args.texto)).model_dump_json(indent=2))
