import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildSteps.dockerCommand
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2024.12"

project {

    buildType(Build)
}

object Build : BuildType({
    name = "Build"

    params {
        password("env.GHCR_TOKEN", "credentialsJSON:ba06c367-fae5-4052-9b2a-f5852770d954")
    }

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        script {
            name = "Docker Login"
            id = "Docker_Login"
            scriptContent = """echo "%env.GHCR_TOKEN%" | docker login ghcr.io -u Postoev-Alexander --password-stdin"""
        }
        script {
            name = "Build and Push Image"
            id = "Build_and_Push_Image"
            scriptContent = """
                echo "=== СТАРТ: Сборка Docker-образа ==="
                sudo docker build -t ghcr.io/postoev-alexander/eos-test-app:0.01 .
                
                echo "=== СТАРТ: Отправка образа на GitHub ==="
                sudo docker push ghcr.io/postoev-alexander/eos-test-app:0.01
                
                echo "=== ВСЁ ГОТОВО! Образ успешно улетел ==="
            """.trimIndent()
        }
        dockerCommand {
            name = "Build Image"
            id = "Build_Image"
            enabled = false
            commandType = build {
                source = file {
                    path = "Dockerfile"
                }
                namesAndTags = "ghcr.io/postoev-alexander/eos-test-app:latest"
                commandArgs = "--pull"
            }
        }
        dockerCommand {
            name = "Push Image to GitHub"
            id = "Push_Image_to_GitHub"
            enabled = false
            commandType = push {
                namesAndTags = "ghcr.io/postoev-alexander/eos-test-app:latest"
            }
        }
        script {
            name = "Build and Push Image (1)"
            id = "Build_and_Push_Image_1"
            enabled = false
            scriptContent = """
                #!/bin/bash
                set -e
                
                echo "=== СТАРТ: Авторизация в GitHub Container Registry ==="
                # Передаем токен явно внутрь sudo, чтобы исключить проблемы с окружением
                export CR_PAT="%env.GHCR_TOKEN%"
                echo "${'$'}CR_PAT" | sudo docker login ghcr.io -u Postoev-Alexander --password-stdin
                
                echo "=== СТАРТ: Сборка Docker-образа ==="
                sudo docker build -t ghcr.io/postoev-alexander/eos-test-app:latest .
                
                echo "=== СТАРТ: Отправка образа на GitHub ==="
                sudo docker push ghcr.io/postoev-alexander/eos-test-app:latest
                
                echo "=== ВСЁ ГОТОВО! ==="
            """.trimIndent()
        }
    }

    triggers {
        vcs {
        }
    }

    features {
        perfmon {
        }
    }
})
