version 1.0

workflow starFusion {
  input {
    File fastq1
    File fastq2
    File? chimeric
  }

  parameter_meta {
    fastq1: "Path to the fastq file for read 1"
    fastq2: "Path to the fastq file for read 2"
    chimeric: "Path to Chimeric.out.junction"
  }

  call runStarFusion { input: fastq1 = fastq1, fastq2 = fastq2, chimeric = chimeric }

  output {
    File fusions = runStarFusion.fusionPredictions
    File fusionsAbridged = runStarFusion.fusionPredictionsAbridged
    File fusionCodingEffects = runStarFusion.fusionCodingEffects 
  }

  meta {
    author: "Heather Armstrong"
    email: "heather.armstrong@oicr.on.ca"
    description: "STAR-Fusion 1.8.1"
  }
}

task runStarFusion {
  input {
    File fastq1
    File fastq2
    File? chimeric
    String? starFusion = "$STAR_FUSION_ROOT/STAR-Fusion"
    Int? cpu = 8
    String? modules = "star-fusion/1.8.1 star-fusion-genome/1.8.1-hg38"
    String? genomeDir = "$STAR_FUSION_GENOME_ROOT/ctat_genome_lib_build_dir"
  }

  parameter_meta {
    fastq1: "Path to the fastq file for read 1"
    fastq2: "Path to the fastq file for read 2"
    chimeric: "Path to Chimeric.out.junction"
    starFusion: "Name of the STAR-Fusion binary"
    cpu: "Number of CPU nodes to use"
    modules: "Names and versions of STAR-Fusion and STAR-Fusion genome to load"
    genomeDir: "Path to the STAR-Fusion genome directory"
  }

  String outdir = "STAR-Fusion_outdir"

  command <<<
      "~{starFusion}" \
      --genome_lib_dir "~{genomeDir}" \
      --left_fq "~{fastq1}" \
      --right_fq "~{fastq2}" \
      --examine_coding_effect \
      --CPU "~{cpu}" --chimeric_junction "~{chimeric}"
  >>>

  runtime {
    modules: "~{modules}"
    cpu: "~{cpu}"
  }

  output {
      File fusionPredictions = "~{outdir}/star-fusion.fusion_predictions.tsv"
      File fusionPredictionsAbridged = "~{outdir}/star-fusion.fusion_predictions.abridged.tsv"
      File fusionCodingEffects = "~{outdir}/star-fusion.fusion_predictions.abridged.coding_effect.tsv"
  }
}
