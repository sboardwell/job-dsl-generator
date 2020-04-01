import groovy.json.JsonSlurper
import groovy.json.JsonOutput

class JobDslGenUtil {

    private static printOutput(output, out) {
        output << '-'.multiply(90)
        output.each {
            out.println "$it"
        }
    }

    private static getResponse(url, body, authorization, postContentType) {
        def http = new URL(url).openConnection() as HttpURLConnection
        http.setRequestMethod("POST")
        http.setDoOutput(true)
        http.setRequestProperty("Authorization", authorization)
        http.setRequestProperty("Accept", "application/json")
        http.setRequestProperty("Content-Type", postContentType)
        http.outputStream.write(body.getBytes("UTF-8"))
        http.connect()
        if (http.responseCode == 200) {
            return http.inputStream.getText("UTF-8")
        } else {
            return http.errorStream.getText("UTF-8")
        }
    }

    static String getJenkinsfileFromGitHub(org, repo, branchStr, scriptPathStr, token = '') {
        def url = "https://raw.githubusercontent.com/${org}/${repo}/${branchStr}/${scriptPathStr}"
        url = token ? "${url}?token=${token}" : url
        return new URL(url).getText()
    }

    static def getJenkinsfileJsonObject(jenkinsUrl, jenkinsCreds, jenkinsfileStr, out) {
        // get the JSON
        String urlText = ''
        def jenkinsPipelineJson
        try {
            String basicAuth = "Basic " + new String(Base64.getEncoder().encode("${jenkinsCreds}".getBytes()));
            urlText = getResponse("${jenkinsUrl}/pipeline-model-converter/toJson", "jenkinsfile=${jenkinsfileStr}", "${basicAuth}", 'application/x-www-form-urlencoded')
        } catch (Exception e) {
            out.println "There was a problem fetching the json. ${e.message}"
            throw e
        }
        // validate JSON
        try {
            jenkinsPipelineJson = new JsonSlurper().parseText(urlText)
            // validate the response
            if (jenkinsPipelineJson.status != 'ok' || jenkinsPipelineJson.data.result != 'success') {
                out.println "START\n${jenkinsPipelineJson.toString()}\nEND"
                out.println JsonOutput.prettyPrint("${urlText}")
                throw new Exception("Pipeline parsing error. See: the response above.")
            }
        } catch (Exception e) {
            out.println "There is a problem with the returned Jenkinsfile JSON object:"
            out.println "-".multiply(80)
            out.println "Jenkinsfile sent:"
            out.println "$jenkinsfileStr"
            out.println "-".multiply(80)
            out.println "Response from Jenkins:"
            out.println urlText
            throw e
        }
        return jenkinsPipelineJson.data.json
    }

    static def generateJobFromScratch(dslFactory, jobName, org, repo, branchStr, credentialsId, scriptPathStr, displayName, description, githubToken, jenkinsUrl, jenkinsCreds) {
        dslFactory.out.println "Generating pipeline job from scratch for '${jobName}'"
        String jenkinsfileStr = getJenkinsfileFromGitHub(org, repo, branchStr, scriptPathStr, githubToken)
        def pipelineJson = getJenkinsfileJsonObject(jenkinsUrl, jenkinsCreds, jenkinsfileStr, dslFactory.out)
        generateJobFull(dslFactory, jobName, org, repo, branchStr, credentialsId, scriptPathStr, displayName, description, pipelineJson, dslFactory.out)
    }

    static def generateJobFull(dslFactory, jobName, org, repo, branchStr, credentialsId, scriptPathStr, displayName, description, pipelineJson, out) {
        def job = generateJobBase(dslFactory, jobName, org, repo, branchStr, credentialsId, scriptPathStr, displayName, description)
        addParameters(job, pipelineJson, out)
        addOptions(job, pipelineJson, out)
        addTriggers(job, pipelineJson, out)
        return job
    }

    static def generateJobBase(dslFactory, jobName, org, repo, branchStr, credentialsId, scriptPathStr, displayName, description) {
        dslFactory.out.println "Generating base job for '${jobName}'"
        def job = dslFactory.pipelineJob("${jobName}") {
            if (displayName) {
                delegate.displayName displayName
            }
            if (description) {
                delegate.description description
            }
            definition {
                cpsScm {
                    scm {
                        git {
                            branch(branchStr)
                            remote {
                                github("${org}/${repo}", 'https')
                                if (credentialsId) {
                                    delegate.credentials(credentialsId)
                                }
                            }
                            extensions {
                                localBranch ''
                            }
                        }
                    }
                    lightweight()
                    scriptPath(scriptPathStr)
                }
            }
        }
        return job
    }

    static def addTriggers(job, pipelineJson, out) {
        def output = []
        try {
            // add triggers
            output << '-'.multiply(90)
            output << 'Calling addTriggers'
            def jobTriggers = pipelineJson.pipeline.triggers?.triggers
            if (jobTriggers) {
                job.with {
                    triggers {
                        triggersDelegate = delegate
                        jobTriggers.each {
                            // currently only supporting literals
                            if (it.arguments[0].isLiteral) {
                                def triggerType = it.name
                                def valueToSet = it.arguments[0].value
                                output << "TRIGGER ----> ${triggerType} : ${valueToSet}"
                                switch (triggerType) {
                                    case ~/cron/:
                                        triggersDelegate.cron {
                                            spec(valueToSet)
                                        }
                                        break
                                    case ~/pollSCM/:
                                        triggersDelegate.pollSCM {
                                            scmpoll_spec(valueToSet)
                                        }
                                        break
                                    default:
                                        throw new Exception("Unhandled triggerType option.")
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            printOutput(output, out)
        }
    }

    static def addParameters(job, pipelineJson, out) {
        def output = []
        try {
            // add parameters
            output << '-'.multiply(90)
            output << 'Calling addParameters'
            def jobParameters = pipelineJson.pipeline.parameters?.parameters
            if (jobParameters) {
                job.with {
                    parameters {
                        def parametersDelegate = delegate
                        jobParameters.each { paramObj ->
                            def paramName = paramObj.arguments.find { a -> a.key == 'name' }.value.value
                            def paramType = paramObj.name
                            def argMap = [:]
                            output << "PARAMETER ----> $paramType :"
                            paramObj.arguments.each { arg ->
                                String argKey = arg.key
                                boolean isLiteral = arg.value.isLiteral as boolean
                                // currently only supporting literals
                                // if non-literal values found set to blank string to be corrected on first build run 
                                def valueToSet = ''
                                if (isLiteral)  {
                                    valueToSet = arg.value.value
                                }
                                output << "    Use literal: ${isLiteral} ---------> $argKey : ${valueToSet}"
                                argMap."$argKey" = valueToSet
                            }
                            switch (paramType) {
                                case ~/booleanParam/:
                                    addBooleanParam(parametersDelegate, argMap)
                                    break
                                case ~/string/:
                                    addStringParam(parametersDelegate, argMap)
                                    break
                                case ~/choice/:
                                    addChoiceParam(parametersDelegate, argMap)
                                    break
                                case ~/text/:
                                    addTextParam(parametersDelegate, argMap)
                                    break
                                default:
                                    throw new Exception("Unhandled paramType option.")
                            }
                        }
                    }
                }
            }
        } finally {
            printOutput(output, out)
        }
    }

    static def addOptions(job, pipelineJson, out) {
        def output = []
        try {
            output << '-'.multiply(90)
            output << 'Calling addOptions'
            def jobOptions = pipelineJson.pipeline.options?.options
            if (jobOptions) {
                job.with {
                    //noinspection GroovyAssignabilityCheck
                    properties {
                        jobOptions.each { optionObj ->
                            def optionType = optionObj.name
                            output << "OPTION ----> $optionType"
                            switch (optionType) {
                                case ~/disableConcurrentBuilds/:
                                    delegate.disableConcurrentBuilds()
                                    break
                                case ~/skipDefaultCheckout/:
                                    output << "Ignoring pipeline only '${optionType}"
                                    break
                                case ~/skipStagesAfterUnstable/:
                                    output << "Ignoring pipeline only '${optionType}"
                                    break
                                case ~/timestamps/:
                                    output << "Ignoring pipeline only '${optionType}"
                                    break
                                case ~/timeout/:
                                    output << "Ignoring pipeline only '${optionType}"
                                    break
                                case ~/buildDiscarder/:
                                    if (optionObj.arguments) {
                                        // currently only supporting the logRotator method
                                        def argMap = [:]
                                        def logRotatorArgs = optionObj.arguments.find { a -> a.name == 'logRotator' }?.arguments
                                        logRotatorArgs?.each { arg ->
                                            String argKey = arg.key
                                            boolean isLiteral = arg.value.isLiteral as boolean
                                            // currently only supporting literals
                                            // if non-literal values found set to blank string to be corrected on first build run 
                                            def valueToSet = ''
                                            if (isLiteral)  {
                                                valueToSet = arg.value.value
                                            }
                                            output << "    Use literal: ${isLiteral} ---------> $argKey : ${valueToSet}"
                                            argMap."${argKey}" = valueToSet
                                        }
                                        delegate.buildDiscarder {
                                            strategy {
                                                logRotator {
                                                    daysToKeepStr(argMap."daysToKeepStr" ?: '')
                                                    numToKeepStr(argMap."numToKeepStr" ?: '')
                                                    artifactDaysToKeepStr(argMap."artifactDaysToKeepStr" ?: '')
                                                    artifactNumToKeepStr(argMap."artifactNumToKeepStr" ?: '')
                                                }
                                            }
                                        }
                                    }
                                    break
                                default:
                                    throw new Exception("Unhandled optionType '${optionType}.")
                            }
                        }
                    }
                }
            }
        } finally {
            printOutput(output, out)
        }
    }

    private static addStringParam(def delegate, def argMap) {
        delegate.stringParam(argMap.name, argMap.defaultValue, argMap.description)
    }

    private static addChoiceParam(def delegate, def argMap) {
        // special case for choices
        if (argMap.choices instanceof String) {
            argMap.choices = argMap.choices.split('\n') as List<String>
        }
        delegate.choiceParam(argMap.name, argMap.choices, argMap.description)
    }

    private static addTextParam(def delegate, def argMap) {
        delegate.textParam(argMap.name, argMap.defaultValue, argMap.description)
    }

    private static addBooleanParam(def delegate, def argMap) {
        delegate.booleanParam(argMap.name, argMap.defaultValue, argMap.description)
    }
}
