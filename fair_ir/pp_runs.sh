for f in `find . -name "input*"`
do
	for s in `seq 1 1 100`
	do
		cat $f | awk -v s=$s '{if ($2==s) print $1 "\t" $3 }' > $f.$s 
	done
done
