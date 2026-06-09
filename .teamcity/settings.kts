import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
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
        param("TARGET_HOST", "rate.test.eos.winzardy.com")
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
            enabled = false
            scriptContent = """sudo CR_PAT="%env.GHCR_TOKEN%" bash .cicd/build.sh --push"""
        }
        script {
            name = "deploy"
            id = "deploy"
            scriptContent = """
                #!/bin/bash
                set -e
                
                # Настройки SSH для автоматизации: отключаем вопросы про Host Key и передаем ключ
                SSH_OPTS="-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i %teamcity.ssh.private.key.path%"
                
                echo "=== СТАРТ: Копируем docker-compose.yml на целевой сервер ==="
                # Создаем папку на сервере
                ssh ${'$'}SSH_OPTS %TARGET_USER%@%TARGET_HOST% "mkdir -p ~/eos-test-app"
                
                # Перебрасываем файл через scp с нашим ключом
                scp ${'$'}SSH_OPTS docker-compose.yml %TARGET_USER%@%TARGET_HOST%:~/eos-test-app/docker-compose.yml
                
                echo "=== СТАРТ: Выполнение команд деплоя на сервере %TARGET_HOST% ==="
                ssh ${'$'}SSH_OPTS %TARGET_USER%@%TARGET_HOST% "
                    cd ~/eos-test-app
                
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
        }
    }

    features {
        perfmon {
        }
    }
})
