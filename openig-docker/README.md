# How-to:
Build docker image:

    docker build . -t openidentityplatform/openig

Run image

    docker run -h openig-01.domain.com -p 8080:8080 --name openig-01 openidentityplatform/openig
