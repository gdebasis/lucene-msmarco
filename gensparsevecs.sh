#!/bin/bash

#rm -rf topdocs/*
#rm -rf sparsevecs/
#mvn exec:java -Dexec.mainClass="retrieval.OneStepRetriever"
mvn exec:java -Dexec.mainClass="indexing.SparseVecWriter"
#mvn exec:java@gensparse
