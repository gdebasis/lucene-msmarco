#!/bin/bash

if [ $# -lt 3 ]
then
        echo "Usage: $0 <dl19 res> <dl20 res> <method (nqc/jm/corel)>"
        exit
fi

mvn exec:java -Dexec.mainClass="experiments.TRECDLQPPEvaluator" -Dexec.args="$1 $2 $3"
