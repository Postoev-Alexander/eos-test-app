import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildFeatures.sshAgent
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

    buildType(Deploy)
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
            enabled = false
            scriptContent = """echo "%env.GHCR_TOKEN%" | sudo docker login ghcr.io -u Postoev-Alexander --password-stdin"""
        }
        script {
            name = "Build and Push Image"
            id = "Build_and_Push_Image"
            enabled = false
            scriptContent = """
                echo "=== СТАРТ: Сборка Docker-образа ==="
                sudo docker build -t ghcr.io/postoev-alexander/eos-test-app:0.01 .
                
                echo "=== СТАРТ: Отправка образа на GitHub ==="
                sudo docker push ghcr.io/postoev-alexander/eos-test-app:0.01
                
                echo "=== ВСЁ ГОТОВО! Образ успешно загружен ==="
            """.trimIndent()
        }
        script {
            name = "Build and Push Image (1)"
            id = "simpleRunner"
            scriptContent = """sudo CR_PAT="%env.GHCR_TOKEN%" bash .cicd/build.sh --push"""
        }
    }

    triggers {
        vcs {
            enabled = false
        }
    }

    features {
        perfmon {
        }
    }
})

object Deploy : BuildType({
    name = "Deploy"

    params {
        param("TARGET_USER", "root")
        param("TARGET_HOST", "rate.test.eos.winzardy.com")
        password("env.GHCR_TOKEN", "credentialsJSON:ba06c367-fae5-4052-9b2a-f5852770d954")
    }

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        script {
            name = "deploy"
            id = "deploy"
            scriptContent = """
                #!/bin/bash
                set -e
                
                echo "=== СТАРТ: Деплой через удаленный контекст Docker ==="
                
                # Передаем пароль в переменную для Docker SSH соединения
                export SSHPASS="uw#-DVX7T657j-"
                
                # Говорим локальной утилите docker compose работать напрямую с удаленным сервером
                # Используем sshpass, чтобы докер сам авторизовался на сервере
                sudo -E sshpass -e docker -H "ssh://root@72.56.41.35" login ghcr.io -u Postoev-Alexander --password-stdin <<EOF
                %env.GHCR_TOKEN%
                EOF
                
                echo "=== Локальный докер дает команду удаленному серверу стянуть образ ==="
                sudo -E sshpass -e docker-compose -H "ssh://root@72.56.41.35" pull
                
                echo "=== Локальный докер перезапускает контейнер на сервере ==="
                sudo -E sshpass -e docker-compose -H "ssh://root@72.56.41.35" up -d
                
                echo "=== Деплой завершен! ==="
            """.trimIndent()
        }
    }

    triggers {
        vcs {
            enabled = false
        }
    }

    features {
        perfmon {
        }
        sshAgent {
            teamcitySshKey = "w_ansible"
        }
    }
})
