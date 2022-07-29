#!/bin/bash

if [ $# -lt 2 ]
then
        echo "Usage: $0 <res file name> <num top docs>"
        exit
fi

mvn exec:java -Dexec.mainClass="retrieval.FdbkTermStats" -Dexec.args="$1 $2"
