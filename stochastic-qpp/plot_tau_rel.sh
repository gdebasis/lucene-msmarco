paste nqc_AP-tau.dat rsd_AP-tau.dat uef_nqc_AP-tau.dat > tau_errors.dat

cat > plot.gnu <<EOF1

# Set the output file type
set terminal postscript eps enhanced font "Helvetica,28"
# Set the output file name
set output 'output.eps'
set xrange [10:100]
set yrange [0:0.5]
set xtics 10, 20, 100 
set ytics 0, 0.1, 0.5
set xtics out 
set ytics out 
set xtics font ", 28" 
set ytics font ", 28" 
set format y '%.1f'
set key bottom right
set key font ",28"
set xlabel '{/Symbol-Oblique M}'
set xlabel font ",28"
set ylabel '{/Symbol D}{/Symbol-Oblique t}'
set ylabel font ",28"

# Now plot the data with lines and points
plot 'tau_errors.dat' using 1:4 w lp pt 1 lw 2 title 'NQC',\
	'tau_errors.dat' using 1:8 w lp pt 2 lw 2 title 'RSD',\
	'tau_errors.dat' using 1:12 w lp pt 3 lw 2 title 'UEF'

EOF1

gnuplot plot.gnu
pstopdf output.eps
mv output.pdf tau_vs_cutoff.pdf
rm output.eps

