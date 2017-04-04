#! /bin/sh

echo "Number of concurrencies: $1"

CONCURRENCIES=$1
java -jar cf-coapbench-1.1.0-SNAPSHOT.jar -c $CONCURRENCIES -t 30 -y FileBlockchain.txt coap://[::1]:3000/authorized &
java -jar cf-coapbench-1.1.0-SNAPSHOT.jar -c $CONCURRENCIES -t 30 -y FileBlockchain.txt coap://[::1]:3001/authorized &
