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
        password("env.GHCR_TOKEN", "credentialsJSON:ba06c367-fae5-4052-9b2a-f5852770d954")
        param("TARGET_HOST", "147.45.158.68")
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
                
                TARGET_HOST="147.45.158.68"
                
                # Опции, чтобы ssh не задавал интерактивных вопросов в логах TeamCity
                SSH_OPTS="-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null"
                
                echo "=== СТАРТ: Копируем docker-compose.yml на целевой сервер ==="
                # Создаем папку проекта на сервере (ключ подхватится из SSH Agent автоматически)
                ssh ${'$'}SSH_OPTS root@${'$'}TARGET_HOST "mkdir -p ~/eos-test-app"
                
                # Копируем файл docker-compose.yml из репозитория на сервер
                scp ${'$'}SSH_OPTS docker-compose.yml root@${'$'}TARGET_HOST:~/eos-test-app/docker-compose.yml
                
                
                echo "=== СТАРТ: Выполнение команд деплоя на сервере ==="
                # Подключаемся по SSH и запускаем докер прямо на сервере
                ssh ${'$'}SSH_OPTS root@${'$'}TARGET_HOST "
                    cd ~/eos-test-app
                
                    # Авторизуем Docker сервера в GitHub Packages
                    echo '%env.GHCR_TOKEN%' | docker login ghcr.io -u Postoev-Alexander --password-stdin
                
                    echo '=== Сервер: Скачиваем свежий образ ==='
                    docker compose pull
                
                    echo '=== Сервер: Перезапускаем контейнер ==='
                    docker compose up -d
                
                    echo '=== Сервер: Очищаем старые образы ==='
                    docker image prune -f
                "
                
                echo "=== ДЕПЛОЙ ПОЛНОСТЬЮ ЗАВЕРШЕН! ==="
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
