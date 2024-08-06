cat > plot.gnu <<EOF1

set terminal postscript eps enhanced font "Helvetica,28"
set output 'output.eps'

plot [10:100] [0:1] real(x * log(x))/1000 title 'xlog(x)'
EOF1

gnuplot plot.gnu
pstopdf output.eps
mv output.pdf bounds.pdf

rm output.eps


