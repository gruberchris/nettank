# Nettank

A multiplayer top down 2D tank battle game where players control tanks and try to destroy each other.

## Launching The Game Server

The gamer server can be deployed using Docker. The following command will build the Docker image and run the server:

To build the Docker image:

```shell
docker build -t nettank-server .
```

To run the Docker container using interactive mode:

```shell
docker run -p 5555:5555 nettank-server
```

To run the Docker container in detached mode (background):

```shell
docker run -d -p 5555:5555 --restart unless-stopped nettank-server
```