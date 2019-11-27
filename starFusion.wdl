version 1.0

workflow starFusion {
  input {
    Array[Pair[File, File]] inputFqs
    File? chimeric
  }

  scatter (fq in inputFqs) {
    File fastq1    = fq.left
    File fastq2    = fq.right
  }

  parameter_meta {
    inputFqs: "Array of fastq read pairs"
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
    dependencies: [
     {
      name: "star/2.7.3a",
      url: "https://github.com/alexdobin/STAR"
     },
     {
      name: "star-fusion/1.8.1",
      url: "https://github.com/STAR-Fusion/STAR-Fusion/wiki"
     }
    ]
  }

}

task runStarFusion {
  input {
    Array[File] fastq1
    Array[File] fastq2
    File? chimeric
    String? starFusion = "$STAR_FUSION_ROOT/STAR-Fusion"
    Int? cpu = 8
    String? modules = "star-fusion/1.8.1 star-fusion-genome/1.8.1-hg38"
    String? genomeDir = "$STAR_FUSION_GENOME_ROOT/ctat_genome_lib_build_dir"
  }

  parameter_meta {
    fastq1: "Array of paths to the fastq files for read 1"
    fastq2: "Array of paths to the fastq files for read 2"
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
      --left_fq ~{sep="," fastq1} \
      --right_fq ~{sep="," fastq2} \
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
