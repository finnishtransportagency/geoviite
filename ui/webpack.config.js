/* eslint-disable @typescript-eslint/no-var-requires */
const dotenv = require('dotenv');
const path = require('path');
const webpack = require('webpack');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const ESLintWebpackPlugin = require('eslint-webpack-plugin');
const LicensePlugin = require('webpack-license-plugin');
const CspHtmlWebpackPlugin = require('csp-html-webpack-plugin');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const CopyPlugin = require('copy-webpack-plugin');

const { execSync } = require('child_process');

dotenv.config();

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
    'OFL-1.1',
];
const licenseOverrides = {};

const glyphLocations = [
    path.resolve(__dirname, 'src/geoviite-design-lib/glyphs'),
    path.resolve(__dirname, 'src/vayla-design-lib/icon'),
];

// Used when building the frontend without the development server, such as in a CI pipeline.
class SynchronizedScssTypingsPlugin {
    createScssTypings() {
        console.log('Updating SCSS typings...');
        try {
            execSync('npm run scss', { stdio: 'inherit' });
            console.log('SCSS typings updated.');
        } catch (error) {
            console.error('Error updating SCSS typings:', error);
        }
    }

    apply(compiler) {
        compiler.hooks.afterEnvironment.tap('SynchronizedScssTypingsPlugin', (compilation) => {
            this.createScssTypings();
        });
    }
}

module.exports = (env) => {
    return {
        entry: './src/index.tsx',
        devtool: 'source-map',
        resolve: {
            extensions: ['.ts', '.tsx', '.js', '.json', '.css', '.scss'],
            modules: [path.resolve('./node_modules'), path.resolve(__dirname + '/src')],
        },
        devServer: {
            host: '127.0.0.1',
            port: 9001,
            compress: false,
            proxy: [
                {
                    context: '/api',
                    target: 'http://127.0.0.1:8080',
                    logLevel: 'debug',
                    pathRewrite: { '^/api': '' },
                },
                {
                    context: '/redirect/projektivelho/files',
                    target: process.env.PROJEKTIVELHO_REDIRECT_URL,
                    logLevel: 'debug',
                    changeOrigin: true,
                    router: function (req) {
                        const documentOid = req.query['document'];
                        const assignmentOid = req.query['assignment'];
                        const projectOid = req.query['project'];
                        const projectGroupOid = req.query['projectGroup'];

                        if (projectOid) {
                            const projectPath = `/projektit/oid-${projectOid}`;

                            const assigmentPath = assignmentOid
                                ? `/toimeksiannot/oid-${assignmentOid}`
                                : '';

                            const documentPath =
                                assignmentOid && documentOid
                                    ? `/aineistot/oid-${documentOid}/muokkaa`
                                    : '';

                            return projectPath + assigmentPath + documentPath;
                        } else if (projectGroupOid) {
                            return `/projektijoukot/oid-${projectGroupOid}`;
                        }

                        //Returning undefined would redirect uri as it is to ProjektiVelho, and we don't want that
                        return '';
                    },
                },
                ...(process.env.MML_MAP_IN_USE === 'true'
                    ? [
                          {
                              context: '/location-map/',
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
                      ]
                    : []),
            ],
        },
        output: {
            path: path.join(__dirname, '/dist'),
            filename: 'bundle.js',
            assetModuleFilename: '[name][ext]',
        },
        module: {
            rules: [
                {
                    test: /\.tsx?$/,
                    loader: 'swc-loader',
                },
                {
                    test: /\.(png|jp(e*)g|gif|woff|woff2|ttf|eot|ico|otf)$/,
                    type: 'asset/resource',
                },

                {
                    test: /\.svg$/,
                    include: glyphLocations,
                    type: 'asset/source',
                },
                {
                    test: /\.svg$/,
                    exclude: glyphLocations,
                    type: 'asset/resource',
                },
                {
                    test: /\.(sass|scss)$/i,
                    use: [
                        // Creates `style` nodes from JS strings
                        MiniCssExtractPlugin.loader,
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
                    test: /\.css$/i,
                    include: /node_modules/,
                    use: [MiniCssExtractPlugin.loader, 'css-loader'],
                },
            ],
        },
        plugins: [
            new SynchronizedScssTypingsPlugin(),
            new HtmlWebpackPlugin({
                template: './src/index.html',
                favicon: './src/geoviite-design-lib/geoviite-logo.svg',
            }),
            new MiniCssExtractPlugin({ insert: ':last-child(meta)' }),
            // NOTE: According to this post this plugin is bad and headers should be used instead
            // https://towardsdatascience.com/content-security-policy-how-to-create-an-iron-clad-nonce-based-csp3-policy-with-webpack-and-nginx-ce5a4605db90
            new CspHtmlWebpackPlugin(
                {
                    'base-uri': "'self'",
                    'object-src': "'none'",
                    'img-src': 'data: http: https:', // http: is for running locally
                    // Remove explicit style-src and favor nonces when react-select can be made to use non-inline styles
                    'style-src': "'unsafe-inline' http: https:",
                    'default-src': "'self'",
                    'connect-src': 'http: https: ws:',
                },
                {
                    enabled: true,
                    hashingMethod: 'sha256',
                    nonceEnabled: {
                        'script-src': true,
                        // Enable nonce for style-src when react-select can be made to use non-inline styles
                        'style-src': false,
                    },
                },
            ),
            new ESLintWebpackPlugin({
                configType: 'flat',
                extensions: ['js', 'jsx', 'ts', 'tsx'],
            }),
            new LicensePlugin({
                outputFilename: 'oss-licenses.json',
                licenseOverrides: licenseOverrides,
                unacceptableLicenseTest: (licenseIdentifier) => {
                    return !acceptedLicenses.includes(licenseIdentifier);
                },
            }),
            new CopyPlugin({
                patterns: [
                    {
                        from: path.resolve(__dirname, '../LICENSE.txt'),
                        to: path.resolve(__dirname, 'dist/LICENSE.txt'),
                    },
                ],
            }),
        ],
    };
};
