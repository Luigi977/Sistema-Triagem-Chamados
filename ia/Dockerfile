FROM python:3.12-slim
WORKDIR /app
COPY requirements.lock.txt .
RUN pip install --no-cache-dir -r requirements.lock.txt
COPY . .
ENV MPLCONFIGDIR=/tmp/matplotlib
RUN useradd --uid 10001 --create-home triagem && chown -R triagem:triagem /app
USER triagem
EXPOSE 8000
CMD ["uvicorn", "src.main:app", "--host", "0.0.0.0", "--port", "8000", "--no-access-log"]
