#!/bin/bash

if [ $# -lt 2 ]
then
        echo "Usage: $0 <collection file> <index folder>"
        exit
fi

mvn exec:java -Dexec.mainClass="indexing.MsMarcoIndexer" -Dexec.args="$1 $2"
