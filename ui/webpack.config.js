/* eslint-disable @typescript-eslint/no-var-requires */
require('dotenv').config();
const path = require('path');
const webpack = require('webpack');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const ESLintWebpackPlugin = require('eslint-webpack-plugin');
const LicensePlugin = require('webpack-license-plugin');

// If non-allowed licenses are found, you may have added a dependency with a new license type.
// First, ensure it's licensing conditions are compatible with EUPL and our use.
// - Official compatibility list can be found at https://joinup.ec.europa.eu/collection/eupl/matrix-eupl-compatible-open-source-licences"
// Then, you may add it on the list here, allowing it to be included in the build.
const acceptedLicenses = [
    'EUPL-1.2',
    'MIT',
    'ISC',
    'BSD-2-Clause',
    'BSD-3-Clause',
    'Apache-2.0',
    'Python-2.0',
    '0BSD',
    'CC0-1.0',
    'CC-BY-4.0',
    'Unlicense',
    'Zlib',
    'EPL-1.0',
    '(MIT AND Zlib)',
];
const licenseOverrides = {
    // In package.json this is "(EDL-1.0 OR EPL-1.0)". For automation, choose EPL
    'turf-jsts@1.2.3': 'EPL-1.0',
};

module.exports = (env) => {
    return {
        entry: './src/index.tsx',
        devtool: 'source-map',
        resolve: {
            extensions: ['.ts', '.tsx', '.js', '.json', '.css', '.scss'],
            modules: [path.resolve('./node_modules'), path.resolve('./src')],
        },
        devServer: {
            port: 9000,
            proxy: {
                '/api': {
                    target: 'http://localhost:8080',
                    logLevel: 'debug',
                    pathRewrite: { '^/api': '' },
                },
                '/map': {
                    target: 'http://localhost:8081/geoserver/Geoviite/wms',
                    logLevel: 'debug',
                    pathRewrite: (url) => url.replace(/^\/map/, ''),
                },
                ...(process.env.MML_MAP_IN_USE === 'true' && {
                    '/location-map/': {
                        target: process.env.MML_MAP_URL,
                        logLevel: 'debug',
                        pathRewrite: { '^/location-map': '' },
                        changeOrigin: true,
                        headers: {
                            'X-API-Key': process.env.MML_MAP_API_KEY,
                        },
                        onProxyRes: (proxyRes) => {
                            proxyRes.headers['Cache-Control'] =
                                'public, max-age=86400, no-transform';
                        },
                    },
                }),
            },
        },
        output: {
            path: path.join(__dirname, '/dist'),
            filename: 'bundle.js',
        },
        module: {
            rules: [
                {
                    test: /\.tsx?$/,
                    loader: 'ts-loader',
                },
                {
                    test: /\.(png|jp(e*)g|gif)$/,
                    type: 'asset/resource',
                },
                // For now import all SVG files as text, this is needed by icon component.
                // If there is a need to import SVG files other way, use more precise test here
                // to keep icon component working.
                {
                    test: /\.svg$/,
                    exclude: /logo\.svg$/,
                    type: 'asset/source',
                },
                {
                    test: /logo\.svg$/,
                    type: 'asset/inline',
                },
                {
                    test: /\.(sass|scss)$/i,
                    use: [
                        // Creates `style` nodes from JS strings
                        'style-loader',
                        // Generate type definitions from css files to get compiler support
                        '@teamsupercell/typings-for-css-modules-loader',
                        // Translates CSS into CommonJS
                        {
                            loader: 'css-loader',
                            options: {
                                importLoaders: 1,
                                modules: {
                                    localIdentName: '[local]',
                                },
                            },
                        },
                        // Compiles Sass to CSS
                        'sass-loader',
                    ],
                },
                {
                    // Do not transform vendor's CSS with CSS-modules
                    test: /\.css$/,
                    include: /node_modules/,
                    use: [
                        // Creates `style` nodes from JS strings
                        'style-loader',
                        // Translates CSS into CommonJS
                        'css-loader',
                    ],
                },
            ],
        },
        plugins: [
            new HtmlWebpackPlugin({
                template: './src/index.html',
            }),
            new ESLintWebpackPlugin({
                extensions: ['js', 'jsx', 'ts', 'tsx'],
            }),
            new LicensePlugin({
                outputFilename: 'oss-licenses.json',
                licenseOverrides: licenseOverrides,
                unacceptableLicenseTest: (licenseIdentifier) => {
                    return !acceptedLicenses.includes(licenseIdentifier);
                },
            }),
        ],
    };
};
