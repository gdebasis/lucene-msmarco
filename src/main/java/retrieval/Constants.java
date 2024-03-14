package retrieval;

public interface Constants {
    String ID_FIELD = "id";
    String CONTENT_FIELD = "words";
    String MSMARCO_COLL = "data/collection.tsv";
    String MSMARCO_INDEX = "index/";
    String MSMARCO_QUERY_INDEX = "query_index/";
    String QRELS_TRAIN = "data/qrels.train.tsv";
    String QUERY_FILE_TRAIN = "data/queries.train.tsv";
    String STOP_FILE = "stop.txt";
    String FEWSHOT_JSON = "fewshot.json";
    String QUERY_FILE_TEST = "data/trecdl/pass_2019.queries";
    //String QUERY_FILE_TEST = "data/fever.tsv";
    //String QUERY_FILE_TEST = "data/queries.dev.small.tsv";
    String QRELS_TEST = "data/trecdl/pass_2019.qrels";
    String RES_FILE = "ColBERT-PRF-VirtualAppendix/BM25/BM25.2019.res";
    String RES_FILE_RERANKED = "res_rlm.txt";
    String SAVED_MODEL = "model.tsv";
    int NUM_WANTED = 50;
    float LAMBDA = 0.9f;
    float LAMBDA_ODDS = Constants.LAMBDA/(1-Constants.LAMBDA);
    int NUM_TOP_TERMS = 5;
    boolean QRYEXPANSION = false;
    boolean RERANK = false;
    int NUM_QUERY_TERM_MATCHES = 3;
    int K = 10;
    int MU = 1000;
    int NUM_EXPANSION_TERMS = 20;
    float MIXING_LAMDA = 0.9f;
    float FDBK_LAMBDA = 0.2f;
    boolean RLM = true;
    int RLM_NUM_TOP_DOCS = 20;
    int RLM_NUM_TERMS = 20;
    float RLM_NEW_TERMS_WT = 0.2f;
    boolean RLM_POST_QE = false;
    float RLM_FDBK_TERM_WT = 0.2f;
    double ROCCHIO_ALPHA = 0.5;
    double ROCCHIO_BETA = 0.35;
    double ROCCHIO_GAMMA = 1-(ROCCHIO_ALPHA + ROCCHIO_BETA);
    int ROCCHIO_NUM_NEGATIVE = 3;
    String TOPDOCS_FOLDER = "topdocs";

    int QPP_JM_COREL_NUMNEIGHBORS = 3;
    int QPP_COREL_MAX_NEIGHBORS = 3;
    int QPP_COREL_MAX_VARIANTS = 10;
    float QPP_COREL_LAMBDA_STEPS = 0.1f;
    int QPP_NUM_TOPK = 50;
    int EVAL_MIN_REL = 2;
    int NDCG_CUTOFF = 10;

    int RBO_NUM_DOCS = 20;
    String QPP_JM_VARIANTS_FILE_W2V = "variants/trecdl_qv_w2v.csv";
    String QPP_JM_VARIANTS_FILE_RLM = "variants/trecdl_qv_rlm.csv";
}
