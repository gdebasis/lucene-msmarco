package retrieval;

public interface Constants {
    String ID_FIELD = "id";
    String CONTENT_FIELD = "words";
    String TREC_FAIR_IR_METADATA = "/Users/debasis/research/fair_ir/metadata.jsonl";
    String TREC_FAIR_IR_STOCHASTIC_RUNS_DIR = "fair_ir/stochastic_runs";
    String TREC_FAIR_IR_RESDIR = "fair_ir/runs";
    String TREC_FAIR_IR_EVALDIR = "fair_ir/evals";
    String TREC_FAIR_IR_COLL = "/Users/debasis/research/fair_ir/coll.jsonl";
    String TREC_FAIR_IR_INDEX = "/Users/debasis/research/fair_ir/index";
    String TREC_FAIR_IR_QUERY_FILE = "fair_ir/topics.tsv";
    String TREC_FAIR_IR_QRELS_FILE = "/Users/debasis/research/supervised-rlm/fair_ir/qrels.txt";
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
    int NUM_WANTED = 100;
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

    int RBO_NUM_DOCS = 20;
    String QPP_JM_VARIANTS_FILE_W2V = "variants/trecdl_qv_w2v.csv";
    String QPP_JM_VARIANTS_FILE_RLM = "variants/trecdl_qv_rlm.csv";
    boolean NORMALISE_SCORES = true;
    boolean QUERYSIM_USE_RBO = true;

    double MSMARCO_PASSAGE_AVG_LEN = 57.25;
    double FAIRNESS_COLL_AVG_LEN = 2.5f;

    String QRELS_DL1920 = "data/trecdl/trecdl1920.qrels";
    String QUERIES_DL1920 = "data/trecdl/trecdl1920.queries";
    //String QUERIES_DL1920 = "data/trecdl/pass_2019.queries.small"; // for unit test
    String BM25_Top100_DL1920 = "stochastic-qpp/trecdl1920.bm25.res";
    String ColBERT_Top100_DL1920 = "stochastic-qpp/trecdl1920.colbert-e2e.res";
    boolean AUTO_SORT_TOP_DOCS = true;
    boolean ALLOW_UNSORTED_TOPDOCS = true;

    boolean WRITE_PERMS = true;
    int NUM_SHUFFLES = 50;

}
