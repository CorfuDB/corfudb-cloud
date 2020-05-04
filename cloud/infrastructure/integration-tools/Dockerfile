FROM openjdk

ADD ./lib /app/lib/
ADD ./*.jar /app/
ADD ./bin/ /app/bin

WORKDIR /app

CMD bin/integration-tools.sh --help
