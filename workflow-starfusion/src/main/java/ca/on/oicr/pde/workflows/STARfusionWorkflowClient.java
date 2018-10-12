package ca.on.oicr.pde.workflows;

import ca.on.oicr.pde.utilities.workflows.OicrWorkflow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import net.sourceforge.seqware.pipeline.workflowV2.model.Command;
import net.sourceforge.seqware.pipeline.workflowV2.model.Job;
import net.sourceforge.seqware.pipeline.workflowV2.model.SqwFile;

/**
 * <p>
 * For more information on developing workflows, see the documentation at
 * <a href="http://seqware.github.io/docs/6-pipeline/java-workflows/">SeqWare
 * Java Workflows</a>.</p>
 *
 * Quick reference for the order of methods called: 1. setupDirectory 2.
 * setupFiles 3. setupWorkflow 4. setupEnvironment 5. buildWorkflow
 *
 * See the SeqWare API for
 * <a href="http://seqware.github.io/javadoc/stable/apidocs/net/sourceforge/seqware/pipeline/workflowV2/AbstractWorkflowDataModel.html#setupDirectory%28%29">AbstractWorkflowDataModel</a>
 * for more information.
 */
public class STARfusionWorkflowClient extends OicrWorkflow {

    //dir
    private String dataDir, tmpDir;
    private String outDir;

    // Input Data
    private String read1Fastq;
    private String read2Fastq;
    private String outputFilenamePrefix;

    // programs
    private String perl;
    private String tabix;
    private String star;
    private String samtools;
    private String starFusion;
    private String perlExport;
    private String tabixExport;
    private String starExport;
    private String samExport;
    private String starFusionExport;
    
    // program opt
    private String fusionInspect;

    //Memory allocation
    private Integer starFusionMem; // in GBs

    //path to bin
    private String bin;

    //ref Data
    private String refGenomeDir;

    private boolean manualOutput;
    private static final Logger logger = Logger.getLogger(STARfusionWorkflowClient.class.getName());
    private String queue;
    private Map<String, SqwFile> tempFiles;

    // meta-types
    private final static String TXT_METATYPE = "text/plain";
    private final static String BAM_METATYPE = "application/bam";
    //private final static String TAR_GZ_METATYPE = "application/tar-gzip";
    private static final String FASTQ_GZIP_MIMETYPE = "chemical/seq-na-fastq-gzip";

    private void init() {
        try {
            //dir
            dataDir = "data";
            tmpDir = getProperty("tmp_dir");

            // input samples 
            read1Fastq = getProperty("input_read1_fastq");
            read2Fastq = getProperty("input_read2_fastq");

            //Ext id
            outputFilenamePrefix = getProperty("external_name");
            
            // fusion inspect
            fusionInspect = getProperty("fusion_inspect");

            // Tools
            perl = getProperty("perl");
            tabix = getProperty("tabix");
            star = getProperty("star");
            samtools = getProperty("samtools");
            starFusion = getProperty("starfusion");
            
            perlExport ="export LD_LIBRARY_PATH=" + this.perl + "/lib:$LD_LIBRARY_PATH" + ";" +
                        "export PERL5LIB=" + this.perl + "/lib:$PERL5LIB" + ";" +
                        "export PATH=" + this.perl + "/bin:$PATH" + ";";
            
            starExport = "export PATH=" + this.star + ":$PATH" + ";";
            
            samExport =  "export PATH=" + this.samtools + ":$PATH" + ";";
            
            starFusionExport = "export PATH=" + this.starFusion + ":$PATH" + ";";
            
            tabixExport =  "export LD_LIBRARY_PATH=" + this.tabix + ":$LD_LIBRARY_PATH" + ";" +
                           "export PATH=" + this.tabix + ":$PATH" + ";" +
                           "export PERL5LIB=" + this.tabix + "/lib/perl5:$PERL5LIB" + ";" +
                           "export TABIXROOT=" + this.tabix + ";";
            
         

            // ref fasta
            refGenomeDir = getProperty("ref_genome_dir");

            manualOutput = Boolean.parseBoolean(getProperty("manual_output"));
            queue = getOptionalProperty("queue", "");

            // starFusion
            starFusionMem = Integer.parseInt(getProperty("starfusion_mem"));

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setupDirectory() {
        init();
        this.addDirectory(dataDir);
        this.addDirectory(tmpDir);
        if (!dataDir.endsWith("/")) {
            dataDir += "/";
        }
        if (!tmpDir.endsWith("/")) {
            tmpDir += "/";
        }
    }

    @Override
    public Map<String, SqwFile> setupFiles() {
        SqwFile file0 = this.createFile("read1");
        file0.setSourcePath(read1Fastq);
        file0.setType(FASTQ_GZIP_MIMETYPE);
        file0.setIsInput(true);
        SqwFile file1 = this.createFile("read2");
        file1.setSourcePath(read2Fastq);
        file1.setType(FASTQ_GZIP_MIMETYPE);
        file1.setIsInput(true);
        return this.getFiles();
    }

    @Override
    public void buildWorkflow() {

        /**
         * STAR-Fusion:
         */
        // workflow : read inputs read1 fastq and read2 fastq file; run star-fusion; write the output to temp directory; 
        // run sequenzaR; handle output; provision files (3) -- .tsv, .tsv, .tsv;
        Job parentJob = null;
        this.outDir = this.outputFilenamePrefix + "_output";

        Job starJob = runStarFusion();
        parentJob = starJob;
        
        // files for provisioning out
        HashMap<String, String> provOut = new HashMap<String, String> (){{
            put("prediction_tsv", "star-fusion.fusion_predictions.tsv");
            put("abridged_tsv", "star-fusion.fusion_predictions.abridged.tsv");
            put("coding_effect_tsv", "star-fusion.fusion_predictions.abridged.coding_effect.tsv");
        }};
        
        
        Job renameOutputs = copyOutputs(provOut);
        renameOutputs.addParent(parentJob);
        parentJob = renameOutputs;

        
        HashMap<String, String> metaTypeMap = new HashMap<String, String>(){{
//            put("junction", TXT_METATYPE);
//            put("bam", BAM_METATYPE);
            put("prediction_tsv", TXT_METATYPE);
            put("abridged_tsv", TXT_METATYPE);
            put("coding_effect_tsv", TXT_METATYPE);
        }};
        
        List<String> keys = new ArrayList<>(provOut.keySet());
        for (String k : keys){
            String provFile = this.dataDir + this.outputFilenamePrefix + "." + provOut.get(k);
            SqwFile provSqwFile = createOutputFile(provFile, metaTypeMap.get(k), this.manualOutput);
            provSqwFile.getAnnotations().put("STAR_fusion_" + k , "STAR_fusion");
            parentJob.addFile(provSqwFile);
        }
        
        String starFusionsDirectory = this.dataDir + this.outputFilenamePrefix + "_STARFusion_results.tar.gz";
        SqwFile fusionInspectorSqw = createOutputFile(starFusionsDirectory, "application/tar-gzip", this.manualOutput);
        fusionInspectorSqw.getAnnotations().put("STAR_fusion_FusionInspector" , "STAR_fusion");
        parentJob.addFile(fusionInspectorSqw);
        
        
//        // bam file 
//        String fusionBamFile = this.tmpDir + this.outputFilenamePrefix + ".std.STAR.bam";
//        SqwFile fusionBam = createOutputFile(fusionBamFile, BAM_METATYPE, this.manualOutput);
//        fusionBam.getAnnotations().put("STAR_fusion_bam", "STAR_fusion");
//        parentJob.addFile(fusionBam);
//        
//        // junction
//        String fusionJunctionFile = this.tmpDir + this.outputFilenamePrefix + ".std.Chimeric.out.junction";
//        SqwFile fusionJunction = createOutputFile(fusionBamFile, TXT_METATYPE, this.manualOutput);
//        fusionJunction.getAnnotations().put("STAR_fusion_bam", "STAR_fusion");
//        parentJob.addFile(fusionJunction);
//        
//        
//        String fusionPredictionTsv = this.tmpDir + this.outputFilenamePrefix + ".star-fusion.fusion_predictions.tsv";
//        SqwFile fusionTSV = createOutputFile(fusionPredictionTsv, TXT_METATYPE, this.manualOutput);
//        fusionTSV.getAnnotations().put("STAR_fusion_prediction_tsv", "STAR_fusion");
//        parentJob.addFile(fusionTSV);
//
//        String fusionAbridgedTsv = this.tmpDir + this.outputFilenamePrefix + ".star-fusion.fusion_predictions.abridged.tsv";
//        SqwFile abridgedTSV = createOutputFile(fusionAbridgedTsv, TXT_METATYPE, this.manualOutput);
//        abridgedTSV.getAnnotations().put("STAR_fusion_abridged_tsv", "STAR_fusion");
//        parentJob.addFile(abridgedTSV);
//
//        String FFP_coding_effect = this.tmpDir + this.outputFilenamePrefix + ".star-fusion.fusion_predictions.abridged.coding_effect.tsv";
//        SqwFile codingTSV = createOutputFile(FFP_coding_effect, TXT_METATYPE, this.manualOutput);
//        codingTSV.getAnnotations().put("STAR_fusion_coding_effect_tsv ", "STAR_fusion");
//        parentJob.addFile(codingTSV);
    }

    private Job runStarFusion() {
        Job starJob = getWorkflow().createBashJob("starfusionjob");
        Command cmd = starJob.getCommand();
        cmd.addArgument(this.perlExport);
        cmd.addArgument(this.starExport);
        cmd.addArgument(this.samExport);
        cmd.addArgument(this.starFusionExport);
        cmd.addArgument(this.tabixExport);
        cmd.addArgument("STAR-Fusion");
        cmd.addArgument("--genome_lib_dir " + this.refGenomeDir);
        cmd.addArgument("--left_fq " + getFiles().get("read1").getProvisionedPath());
        cmd.addArgument("--right_fq " + getFiles().get("read2").getProvisionedPath());
        cmd.addArgument("--examine_coding_effect");
        cmd.addArgument("--FusionInspector " + this.fusionInspect);
        cmd.addArgument("--output_dir " + this.tmpDir);
        starJob.setMaxMemory(Integer.toString(starFusionMem * 1024));
        starJob.setQueue(queue);
        return starJob;
    }
    
    private Job copyOutputs(HashMap<String, String> provOut) {
        List<String> keys = new ArrayList<>(provOut.keySet());
        Job copyPaths = getWorkflow().createBashJob("copyoutputsjob");      
        Command cmd = copyPaths.getCommand();
        for (String k : keys){
            cmd.addArgument("cp " 
                    + this.tmpDir + provOut.get(k) + " " 
                    + this.dataDir + this.outputFilenamePrefix + "." + provOut.get(k) + ";\n");
        }
        cmd.addArgument("tar -xcvf " + this.dataDir + this.outputFilenamePrefix + "_STARFusion_results.tar.gz " + this.tmpDir + ";");
        copyPaths.setMaxMemory(Integer.toString(starFusionMem * 1024));
        copyPaths.setQueue(queue);
        return copyPaths;
    }
}
