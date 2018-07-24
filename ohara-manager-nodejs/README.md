# Ohara Manager

This repository contains Ohara manager itself (an HTTP server built with node.js) and Ohara manager client (Ohara fastdata UIs built with React.js via create-react-app). In the following docs, we refer **Server** as Ohara manager and **Client** as Ohara manager client.

## Initial machine setup

1.  Install [Node.js](https://nodejs.org/en/) 8.11.2 or greater.

2.  Install [Yarn](https://yarnpkg.com/lang/en/) v1.7.0 or greater.

3.  Make sure you're in the project root and use this command to setup the app: `yarn setup`. This will install all the dependencies for both the **Server** and the **Client** as well as creating a production build for the client.

Have issues while setting up? Try the **Having issue** section to troubleshoot.

## Development

> If this is your first time running this project, you need to complete the **Initial machine setup** section

You need to start both **Server** and **Client** server before you can start your development. Follow the instructions below:

**Server:**

Make sure you're in the project root and start the server with:

```sh
yarn start
```

You can start the server and set the configurator API like this:

```sh
CONFIGURATOR_API=http://localhost:1000/v0 yarn start
```

After starting the server, visit `http://localhost:5050` in your browser.

**Client**:

Make you're in the **client** directory and start the dev server with:

```sh
yarn start
```

After starting the dev server, visit `http://localhost:3000` in your browser.

## Build

> Note that this step is only required for the **Client** not the **Server**

You can get the production-ready static files by using the following command:

These static files will be build and put into the **/build** directory.

```sh
yarn build
```

## Having issues?

**While starting the server got an error like this: Error: Cannot find module ${module-name}**

If you're running into this, it's probably that this module is not correctly installed on your machine. You can fix this by simply run:

```sh
yarn # If this doesn't work, try `yarn add ${module-name}
```

After the installation is completed, start the server again.

**Got an error while starting the server on a Linux machine**

[TODO] Test this on a linux machine and add the error message:

You can run this command to increase the limit on the number of files Linux will watch.

```sh
echo fs.inotify.max_user_watches=524288 | sudo tee -a /etc/sysctl.conf && sudo sysctl -p.
```