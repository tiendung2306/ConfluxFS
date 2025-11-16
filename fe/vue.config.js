module.exports = {
    publicPath: process.env.NODE_ENV === 'production' ? '/' : '/',
    outputDir: 'dist',
    assetsDir: 'static',
    lintOnSave: process.env.NODE_ENV !== 'production',
    devServer: {
        port: 3000,
        host: '0.0.0.0',
        allowedHosts: 'all',
        client: {
            webSocketURL: 'ws://0.0.0.0:3000/ws'
        }
    }
}
