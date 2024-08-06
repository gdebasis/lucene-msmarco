for mode in rel random
do

for metric in AP nDCG 
do

DATAFILE=tau_${mode}.${metric}.errors.dat
paste results-${mode}/nqc_${metric}-tau.dat results-${mode}/rsd_${metric}-tau.dat results-${mode}/uef_nqc_${metric}-tau.dat results-${mode}/cumnqc_${metric}-tau.dat > $DATAFILE

cat > plot.gnu <<EOF1

# Set the output file type
set terminal postscript eps enhanced font "Helvetica,28"
# Set the output file name
set output 'output.eps'
set xrange [10:100]
set yrange [0:0.1]
set xtics 10, 20, 100 
set ytics 0, 0.02, 0.1
set xtics out 
set ytics out 
set xtics font ", 28" 
set ytics font ", 28" 
set format y '%.2f'
set key top right
set key font ",28"
set xlabel '{/Symbol-Oblique M}'
set xlabel font ",28"
set ylabel '{/Symbol D}{/Symbol-Oblique t}'
set ylabel font ",28"

# Now plot the data with lines and points
plot '$DATAFILE' using 1:4 w lp pt 1 lw 2 title 'NQC',\
	'$DATAFILE' using 1:8 w lp pt 2 lw 2 title 'RSD',\
	'$DATAFILE' using 1:12 w lp pt 3 lw 2 title 'UEF',\
	'$DATAFILE' using 1:16 w lp pt 4 lw 2 title 'CNQC'

EOF1

gnuplot plot.gnu
pstopdf output.eps
mv output.pdf deltau_vs_cutoff_${mode}_${metric}.pdf
rm output.eps


cat > plot.gnu <<EOF1

# Set the output file type
set terminal postscript eps enhanced font "Helvetica,28"
# Set the output file name
set output 'output.eps'
set xrange [10:100]
set yrange [0.2:0.6]
set xtics 10, 20, 100 
set ytics 0, 0.2, 0.6
set xtics out 
set ytics out 
set xtics font ", 28" 
set ytics font ", 28" 
set format y '%.1f'
set key bottom right
set key font ",28"
set xlabel '{/Symbol-Oblique M}'
set xlabel font ",28"
set ylabel '{/Symbol-Oblique t}'
set ylabel font ",28"

# Now plot the data with lines and points
plot '$DATAFILE' using 1:2 w lp pt 1 lw 2 title 'NQC',\
	'$DATAFILE' using 1:6 w lp pt 2 lw 2 title 'RSD',\
	'$DATAFILE' using 1:10 w lp pt 3 lw 2 title 'UEF',\
	'$DATAFILE' using 1:14 w lp pt 4 lw 2 title 'CNQC'

EOF1

gnuplot plot.gnu
pstopdf output.eps
mv output.pdf tauorig_vs_cutoff_${mode}_${metric}.pdf
rm output.eps

cat > plot.gnu <<EOF1

# Set the output file type
set terminal postscript eps enhanced font "Helvetica,28"
# Set the output file name
set output 'output.eps'
set xrange [10:100]
set yrange [0.2:0.6]
set xtics 10, 20, 100 
set ytics 0, 0.2, 0.6
set xtics out 
set ytics out 
set xtics font ", 28" 
set ytics font ", 28" 
set format y '%.1f'
set key bottom right
set key font ",28"
set xlabel '{/Symbol-Oblique M}'
set xlabel font ",28"
set ylabel '{/Symbol-Oblique t}'
set ylabel font ",28"

# Now plot the data with lines and points
plot '$DATAFILE' using 1:3 w lp pt 1 lw 2 title 'NQC',\
	'$DATAFILE' using 1:7 w lp pt 2 lw 2 title 'RSD',\
	'$DATAFILE' using 1:11 w lp pt 3 lw 2 title 'UEF',\
	'$DATAFILE' using 1:15 w lp pt 4 lw 2 title 'CNQC'

EOF1

gnuplot plot.gnu
pstopdf output.eps
mv output.pdf taustochastic_vs_cutoff_${mode}_${metric}.pdf
rm output.eps



done
done
