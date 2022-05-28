word_dict={}
VOCABFILE="/home/psen/vocab.txt"
RESULTFILE="/home/psen/result.txt"

with open(VOCABFILE,"r") as f:
    for line in f:
        line = line.strip()
        st = line.split("\t")
        word_dict[st[1]]=st[0]

avg=0
val_ar=[]
with open(RESULTFILE,"r") as f:
    for line in f:
        line = line.strip()
        st=line.split("\t")
        docId=st[0]
        print("Docid ",docId)
        docString=st[1]
        tokens=docString.split(" ")
        token_dict={}
        index = 0
        val_ar=[]
        for token in tokens:
            x=[]
            if token in token_dict.keys():
                x=token_dict[token]
            else:
                val_ar.append(token)
            x.append(index)
            token_dict[token]=x
            index=index+1
        val_ar.sort(reverse=True)
        k=0
        stt="Est "
        v=""
        v1=""
        est=[] 
        for val in val_ar:
            #print(val,":")
            if k== 0:
                v=val
            index=token_dict[val]
            for i in index:
                #print("word## ",word_dict[str(i)])
                stt=stt+","+word_dict[str(i)]+":"+str(val)
                est.append(word_dict[str(i)])
                #print(" ")
                k=k+1
            if k == 20:
                v1=val
                break
        #print("\n")
        print(stt+": "+v+" "+v1)

        stt="Orig "
        val_arr=[]
        vMap={}
        orig=[]
        k=0
        with open("/home/psen/sparse_264014/sparse_"+docId+".txt","r") as f3:
            for line in f3:
                line = line.strip()
                ss=line.split(":")
                val_arr.append(ss[1])
                p=[]
                if ss[1] in vMap.keys():
                    p=vMap[ss[1]]
                p.append(ss[0])
                vMap[ss[1]]=p
                
        val_arr.sort(reverse=True)
        for v in val_arr:
            p=vMap[v]
            for p1 in p:
                stt = stt+","+p1+":"+v
                orig.append(p1)
                k=k+1
                if k == 20:
                    break


        print(stt)
        print("\n")

        overlap=0

        for w in orig:
            if w in est:
                overlap = overlap+1
        print("Num of Overlap: ",overlap)
        print("Normalized Overlap ",overlap/20)
        avg = avg+overlap/len(orig)
        print("\n")




print("Avg: ",avg/1000)
