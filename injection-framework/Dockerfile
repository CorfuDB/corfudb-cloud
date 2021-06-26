FROM python:3.9.5
ADD ./ ./injection_framework

WORKDIR ./injection_framework

RUN pip install -r requirements.txt

CMD python verification.py