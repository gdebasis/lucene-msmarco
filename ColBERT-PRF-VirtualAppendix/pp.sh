for dir in `find . -type d -depth 1 | grep -v "git"`
do
for f in `find $dir -type f`
do
#echo $f
cat $f | awk '{print $1 " " $2 " " $3 " " $4+1 " " $5 " " $6}' > $f.pp 
done
done
