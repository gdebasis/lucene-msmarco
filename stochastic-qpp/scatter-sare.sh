for method in nqc RSD uef_nqc cumnqc
do

for cutoff in 100
do

paste results-rel/${method}_AP_$cutoff.dat results-random/${method}_AP_$cutoff.dat | awk '{print $1 " " 1-$2 " " $3 " " 1-$4}' > tmp

cat > plot.gnu <<EOF1

# Set the output file type
set terminal postscript eps enhanced font "Helvetica,28"
# Set the output file name
set output 'output.eps'
set xrange [0.0:1.0]
set yrange [0.0:1.0]
set xtics 0.0, 0.2, 1 
set ytics 0.0, 0.2, 1 
set xtics out 
set ytics out 
set xtics font ", 28" 
set ytics font ", 28" 
set format y '%.1f'
set key top right
set key font ",28"
set xlabel 'AP\@100'
set xlabel font ",28"
set ylabel '{/Symbol D}sARE'
set ylabel font ",28"

# Now plot the data with lines and points
plot 'tmp' using 1:2 with points pt 4 title 'RAS',\
	 'tmp' using 3:4 with points pt 7 title 'UPS'

EOF1

gnuplot plot.gnu
pstopdf output.eps
mv output.pdf perquery_$method.$cutoff.pdf
rm output.eps

done
done
