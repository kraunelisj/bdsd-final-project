import sys

best = -1000000
runtime = 0
print 'acc,time,exp'
for line in open(sys.argv[1]):
    spl = line.split(" ")
    acc = float(spl[0].split(":")[1])
    time = int(spl[1].split(":")[1])
    runtime += time
    if best < acc:
        best = acc
        print str(best)+','+str(runtime)+',random'+str(sys.argv[2])
        
