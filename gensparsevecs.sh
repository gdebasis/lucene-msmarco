#!/bin/bash

rm -rf topdocs/*
mvn exec:java -Dexec.mainClass="retrieval.OneStepRetriever"
