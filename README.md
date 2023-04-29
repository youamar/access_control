# Access control

This project consists of a client-server access control system.
The program allows users to register and login with a username and password, execute certain commands, and disconnect.
The server manages the client's connections and authorizations to execute commands.

## Architecture
```
└── sec
    ├── client
    |   ├── BasicTextClient.java
    |   └── ClientMain.java
    ├── common
    |   ├── BasicMessage.java
    |   ├── MsgType.java
    |   ├── NonReplayableMessage.java
    |   └── TextMessage.java
    └── server
        ├── BasicServer.java
        ├── ServerMain.java
        └── UserDB.java
```

## Build

To build this project, make sure you have Java Development Kit (JDK) installed on your system.
Then, follow the steps below:
- Open two terminals and navigate to the root of the project directory.
- Compile all the Java files by running the following command in one of them :
```
javac sec/client/*.java sec/common/*.java sec/server/*.java
```
This will compile all the Java files in the sec directory and its subdirectories.

## Starting the Server

To start the server, run the following command in the first terminal :
```
java sec.server.ServerMain
```
## Starting the Client

To start the client, run the following command in the second terminal :
```
java sec.client.ClientMain
```

## Commands

The following commands are available to the client :
```
HELLO
REGISTER
LOGIN
FATHER
DISCONNECT
EXIT
```

## Examples

Here are examples of how to use the program :
```
> HELLO hello there
```
```
> REGISTER login password
```
```
> LOGIN login password
```
```
> FATHER you killed my father
```
```
> DISCONNECT login
```
```
> EXIT
```

## Authors
- 54915 - Yahya OUAMAR
- 57396 - Sacha YOKO