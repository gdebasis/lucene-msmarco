#!/bin/bash

if [ $# -lt 1 ] 
then
	echo "usage: $0 <vocabsize>"
	exit
fi 

MSMARCO_COLL=msmarco.stop.stemmed.tsv
DL19_QUERY=dl19.queries
DL19_QUERY_WITH_ID=/Users/debasis/research/common/msmarco/data/pass_2019.queries
DL19_QRELS=/Users/debasis/research/common/msmarco/data/pass_2019.qrels

VOCAB_SIZE=$1
OUTPUT_MODEL_FILE=model.bpe.$VOCAB_SIZE
ENCODED_COLL=msmarco.$VOCAB_SIZE.bpe
ENCODED_QUERIES=dl19.$VOCAB_SIZE.bpe

# Generate the preprocessed collection if not already there
if [ ! -f $MSMARCO_COLL ]; then
	cd ..
	mvn exec:java -Dexec.mainClass="indexing.Preprocessor"
	mv $MSMARCO_COLL tokenization
	cd -
fi 

if [ ! -f $OUTPUT_MODEL_FILE ]; then
	yttm bpe --data $MSMARCO_COLL --model $OUTPUT_MODEL_FILE --vocab_size $VOCAB_SIZE
fi 

if [ ! -f $ENCODED_COLL ]; then
echo "Encoding $MSMARCO_COLL"
yttm encode --model $OUTPUT_MODEL_FILE --output_type subword < $MSMARCO_COLL | sed 's/▁//g' | awk '{print NR-1 "\t" $0}' > $ENCODED_COLL
head -n2 $ENCODED_COLL
fi

if [ ! -f $ENCODED_QUERIES ]; then
echo "Encoding $DL19_QUERY"
yttm encode --model $OUTPUT_MODEL_FILE --output_type subword < $DL19_QUERY | sed 's/▁//g' > $ENCODED_QUERIES

fi

paste $DL19_QUERY_WITH_ID $ENCODED_QUERIES | awk '{print $1 "\t" $4}' > $ENCODED_QUERIES.withid
head -n2 $ENCODED_QUERIES.withid

cd ..

INDEX_DIR=tokenization/index.bpe.$VOCAB_SIZE
if [ ! -d $INDEX_DIR ]; then
	mkdir $INDEX_DIR
	mvn exec:java -Dexec.mainClass="indexing.MsMarcoIndexer" -Dexec.args="tokenization/$ENCODED_COLL $INDEX_DIR"
else
	echo "Index $INDEX_DIR already exists"
fi

echo "Testing the collection"
mvn exec:java -Dexec.mainClass="indexing.IndexTester" -Dexec.args="$INDEX_DIR"


for mu in 50 100 200 500 1000 2000
do
 
echo "Executing retrieval (no stopword, no stem) on BPE tokenized queries"
mvn exec:java -Dexec.mainClass="retrieval.OneStepRetriever" -Dexec.args="tokenization/$ENCODED_QUERIES.withid $mu whitespace"

trec_eval $DL19_QRELS $ENCODED_QUERIES.withid.res

done

cd -
