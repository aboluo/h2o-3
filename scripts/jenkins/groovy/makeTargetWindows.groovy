def call(final pipelineContext, final Closure body) {
  final List<String> FILES_TO_EXCLUDE = [
    '**/rest.log', '**/*prediction*.csv'
  ]

  final List<String> FILES_TO_ARCHIVE = [
    "**/*.log", "**/out.*", "**/*py.out.txt", "**/java*out.txt", "**/*ipynb.out.txt",
    "**/results/*", "**/*tmp_model*",
    "**/h2o-py/tests/testdir_dynamic_tests/testdir_algos/glm/Rsandbox*/*.csv",
    "**/tests.txt", "**/*lib_h2o-flow_build_js_headless-test.js.out.txt",
    "**/*.code", "**/package_version_check_out.txt"
  ]

  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  if (config.hasJUnit == null) {
    config.hasJUnit = true
  }

  if (config.activatePythonEnv == null) {
    config.activatePythonEnv = true
  }

  config.h2o3dir = config.h2o3dir ?: 'h2o-3'

  if (config.customBuildAction == null) {
    config.customBuildAction = """
      if ( "${config.activatePythonEnv}" = 'true' ) {
        echo "Activating Python ${env.PYTHON_VERSION}"
        C:\\Users\\jenkins\\h2o-3\\h2o-py${env.PYTHON_VERSION}\\Scripts\\activate.ps1
      }

      echo "Running Make"
      make -f ${config.makefilePath} ${config.target}
    """
  }

  try {
    execMake(config.customBuildAction, config.h2o3dir)
  } finally {
    if (config.hasJUnit) {
      final GString findCmd = "find ${config.h2o3dir} -type f -name '*.xml'"
      final GString replaceCmd = "${findCmd} -exec sed -i 's/&#[0-9]\\+;//g' {} +"
      echo "Post-processing following test result files:"
      powershell findCmd
      powershell replaceCmd
      pipelineContext.getUtils().archiveJUnitResults(this, config.h2o3dir)
    }
    if (config.archiveFiles) {
      pipelineContext.getUtils().archiveStageFiles(this, config.h2o3dir, FILES_TO_ARCHIVE, FILES_TO_EXCLUDE)
    }
    if (config.archiveAdditionalFiles) {
      echo "###### Archiving additional files: ######"
      echo "${config.archiveAdditionalFiles.join(', ')}"
      pipelineContext.getUtils().archiveStageFiles(this, config.h2o3dir, config.archiveAdditionalFiles, config.excludeAdditionalFiles)
    }
  }
}

private void execMake(final String buildAction, final String h2o3dir) {
  powershell """
    export JAVA_HOME=/usr/lib/jvm/java-8-oracle

    cd ${h2o3dir}
    echo "Linking small and bigdata"
    cmd /c rmdir smalldata
    cmd /c mklink /d smalldata C:\\Users\\jenkins\\h2o-3\\smalldata
    cmd /c rmdir bigdata
    cmd /c mklink /d smalldata C:\\Users\\jenkins\\h2o-3\\bigdata

    ${buildAction}
  """
}

return this
