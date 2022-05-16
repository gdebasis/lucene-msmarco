#!/bin/bash

if [ $# -lt 1 ]
then
        echo "Usage: $0 <res file name>"
        exit
fi

mvn exec:java -Dexec.mainClass="retrieval.SupervisedRLM" -Dexec.args="$1"
