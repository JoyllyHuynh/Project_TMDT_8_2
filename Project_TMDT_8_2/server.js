// Small zero-dependency static server for the current frontend package.
const http = require("http");
const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "src");
const port = Number(process.env.PORT) || 5500;
const contentTypes = {
    ".html": "text/html; charset=utf-8",
    ".js": "text/javascript; charset=utf-8",
    ".css": "text/css; charset=utf-8"
};

http.createServer((request, response) => {
    const requestedPath = decodeURIComponent(request.url.split("?")[0]);
    const filePath = path.resolve(root, `.${requestedPath}`);
    if (!filePath.startsWith(root)) {
        response.writeHead(403);
        response.end("Forbidden");
        return;
    }
    fs.readFile(filePath, (error, data) => {
        if (error) {
            response.writeHead(404);
            response.end("Not found");
            return;
        }
        response.writeHead(200, { "Content-Type": contentTypes[path.extname(filePath)] || "text/plain; charset=utf-8" });
        response.end(data);
    });
}).listen(port, () => console.log(`BookMooch frontend: http://localhost:${port}/vu/auth/login/login.html`));