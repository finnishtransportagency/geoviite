import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import i18n from 'i18next';
import { err, ok, Result } from 'neverthrow';
import { filterNotEmpty } from 'utils/array-utils';
import Cookies from 'js-cookie';

export const API_URI = '/api';

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';

function getCsrfCookie(): string | undefined {
    return Cookies.get('XSRF-TOKEN');
}

const TOKEN_EXPIRED = 'error.unauthorized.token-expired';

const JSON_HEADERS: HeadersInit = {
    'Accept': 'application/json',
    'Content-Type': 'application/json',
};

const createJsonHeaders = () => {
    const csrfToken = getCsrfCookie();
    return csrfToken ? { ...JSON_HEADERS, 'X-XSRF-TOKEN': csrfToken } : JSON_HEADERS;
};

export type ErrorHandler<T> = (response: ApiErrorResponse) => T;

export type ApiError = {
    status: number;
    response: ApiErrorResponse;
};

export type ApiErrorResponse = {
    messageRows: string[];
    correlationId: string;
    timestamp: string;
    localizedMessageKey?: string;
    localizedMessageParams: string[];
    status: number;
};

export type Page<T> = {
    totalCount: number;
    items: T[];
    start: number;
};

const ignoreErrorHandler = defaultValueErrorHandler(undefined);

function defaultValueErrorHandler<T>(defaultValue: T): ErrorHandler<T> {
    return (response: ApiErrorResponse): T => {
        showHttpError(response);
        return defaultValue;
    };
}

export function queryParams(params: Record<string, unknown>): string {
    const stringifiedParameters = Object.keys(params)
        .map((key) => {
            const value = params[key];
            return value != undefined
                ? `${key}=${encodeURIComponent(value.toString())}`
                : undefined;
        })
        .filter((p) => p != undefined);
    return stringifiedParameters.length == 0 ? '' : `?${stringifiedParameters.join('&')}`;
}

export function getNonNull<Output>(path: string, toastFailure = true): Promise<Output> {
    return getNullable<Output>(path, toastFailure).then((requestResult) => {
        if (requestResult === undefined) {
            if (toastFailure) {
                Snackbar.error(i18n.t('error.entity-not-found-on-path', [path]));
            }
            const rv = Promise.reject(Error(`undefined return when querying ${path}`));
            rv.catch(() => {
                console.error('request returned undefined', path);
            });
            return rv;
        } else {
            return requestResult;
        }
    });
}

const wrapApiErrorResponse = Symbol('wrap api error response');
type WrappedApiErrorResponse = { [k in typeof wrapApiErrorResponse]: ApiErrorResponse };
function isWrappedApiError(x: unknown): x is WrappedApiErrorResponse {
    return typeof x === 'object' && x !== null && wrapApiErrorResponse in x;
}

export function getNullable<Output>(
    path: string,
    toastFailure = true,
): Promise<Output | undefined> {
    const rv = executeRequest<undefined, Output, WrappedApiErrorResponse>(
        path,
        undefined,
        (error) => {
            if (toastFailure) {
                Snackbar.error(i18n.t('error.request-failed', [path]));
            }
            return { [wrapApiErrorResponse]: error };
        },
        'GET',
    ).then((requestResult) =>
        isWrappedApiError(requestResult)
            ? Promise.reject(requestResult[wrapApiErrorResponse])
            : requestResult,
    );
    return rv as Promise<Output | undefined>;
}

export function getNonNullAdt<Output>(
    path: string,
    toastFailure = true,
): Promise<Result<Output, ApiErrorResponse>> {
    return getNullableAdt<Output | undefined>(path, toastFailure).then((requestResult) => {
        if (requestResult.isOk() && requestResult.value === undefined) {
            Snackbar.error(i18n.t('error.entity-not-found-on-path', [path]));
            return Promise.reject(Error(`undefined return when querying ${path}`));
        } else {
            return requestResult as Result<Output, ApiErrorResponse>;
        }
    });
}

export function getNullableAdt<Output>(
    path: string,
    toastError = true,
): Promise<Result<Output | undefined, ApiErrorResponse>> {
    return executeRequest<undefined, Output, WrappedApiErrorResponse>(
        path,
        undefined,
        (e) => ({
            [wrapApiErrorResponse]: e,
        }),
        'GET',
    ).then((r) => {
        if (isWrappedApiError(r)) {
            if (toastError) {
                Snackbar.error(i18n.t('error.request-failed', [path]));
            }
            return err(r[wrapApiErrorResponse]);
        } else {
            return ok(r);
        }
    });
}

export async function postIgnoreError<Input, Output>(
    path: string,
    data: Input,
): Promise<Output | undefined> {
    return executeRequest<Input, Output, undefined>(path, data, ignoreErrorHandler, 'POST');
}

export async function putIgnoreError<Input, Output>(
    path: string,
    data: Input,
): Promise<Output | undefined> {
    return executeRequest<Input, Output, undefined>(path, data, ignoreErrorHandler, 'PUT');
}

export async function deleteIgnoreError<Output>(path: string): Promise<Output | undefined> {
    return executeRequest<undefined, Output, undefined>(
        path,
        undefined,
        ignoreErrorHandler,
        'DELETE',
    );
}

export async function postAdt<Input, Output>(
    path: string,
    data: Input,
    showErrorMessage = false,
): Promise<Result<Output, ApiErrorResponse>> {
    return await executeBodyRequestAdt<Input, Output>(path, data, 'POST', showErrorMessage).then(
        verifyExistsAdt,
    );
}

export async function putAdt<Input, Output>(
    path: string,
    data: Input,
    showErrorMessage = false,
): Promise<Result<Output, ApiErrorResponse>> {
    return await executeBodyRequestAdt<Input, Output>(path, data, 'PUT', showErrorMessage).then(
        verifyExistsAdt,
    );
}

export async function deleteAdt<Input, Output>(
    path: string,
    data: Input,
    showErrorMessage = false,
): Promise<Result<Output, ApiErrorResponse>> {
    return await executeBodyRequestAdt<Input, Output>(path, data, 'DELETE', showErrorMessage).then(
        verifyExistsAdt,
    );
}

export async function postFormIgnoreError<Output>(
    path: string,
    data: FormData,
): Promise<Output | undefined> {
    return postFormWithError<Output, undefined>(path, data, ignoreErrorHandler);
}

export async function postFormWithError<Output, ErrorOutput>(
    path: string,
    data: FormData,
    errorHandler: ErrorHandler<ErrorOutput>,
): Promise<Output | ErrorOutput> {
    const result: Result<Output | undefined, ApiErrorResponse> = await executeBodyRequestInternal(
        () => getFormResponse(path, data, 'POST'),
        true,
    );

    if (result.isOk() && result.value) return result.value;
    else if (result.isOk()) return Promise.reject('Form return missing despite OK result');
    else return errorHandler(result.error);
}

export async function putFormIgnoreError<Output>(
    path: string,
    data: FormData,
): Promise<Output | undefined> {
    const result: Result<Output | undefined, ApiErrorResponse> = await executeBodyRequestInternal(
        () => getFormResponse(path, data, 'PUT'),
        true,
    );

    if (result.isOk()) return result.value;
    else return ignoreErrorHandler(result.error);
}

async function executeRequest<Input, Output, ErrorOutput>(
    path: string,
    data: Input | undefined,
    errorHandler: (response: ApiErrorResponse) => ErrorOutput,
    method: HttpMethod,
): Promise<Output | ErrorOutput | undefined> {
    const result: Result<Output | undefined, ApiErrorResponse> = await executeBodyRequestInternal(
        () => getResponse(path, data, method),
        true,
    );
    if (result.isOk()) return result.value;
    else return errorHandler(result.error);
}

async function executeBodyRequestAdt<Input, Output>(
    path: string,
    data: Input | undefined,
    method: HttpMethod,
    showErrorMessage = false,
): Promise<Result<Output | undefined, ApiErrorResponse>> {
    const result: Result<Output | undefined, ApiErrorResponse> = await executeBodyRequestInternal(
        () => getResponse(path, data, method),
        true,
    );

    if (showErrorMessage) {
        result.mapErr((errorResponse: ApiErrorResponse) => showHttpError(errorResponse));
    }
    return result;
}

async function executeBodyRequestInternal<Output>(
    fetchFunction: () => Promise<Response>,
    retryOnTokenExpired: boolean,
): Promise<Result<Output | undefined, ApiErrorResponse>> {
    const response = await fetchFunction();

    if (response.status === 204) {
        return ok(undefined);
    } else if (response.ok) {
        return ok(await response.json());
    } else {
        const errorResponse: ApiError = await convertResponseToError(response);

        if (
            retryOnTokenExpired &&
            response.status === 401 &&
            errorResponse.response.localizedMessageKey === TOKEN_EXPIRED
        ) {
            return executeBodyRequestInternal(fetchFunction, false);
        } else {
            if (
                response.status === 401 &&
                (response.headers.has('session-expired') ||
                    errorResponse.response.localizedMessageKey === TOKEN_EXPIRED)
            )
                Snackbar.sessionExpired();
            return err(errorResponse.response);
        }
    }
}

async function getFormResponse(
    path: string,
    data: FormData,
    method: HttpMethod,
): Promise<Response> {
    return await fetch(path, {
        method: method,
        credentials: 'same-origin',
        headers: { 'X-XSRF-TOKEN': getCsrfCookie() || '' },
        body: data,
    });
}

async function getResponse<Input>(
    path: string,
    data: Input | undefined,
    method: HttpMethod,
): Promise<Response> {
    return await fetch(path, {
        method: method,
        headers: createJsonHeaders(),
        ...(data !== undefined && { body: JSON.stringify(data) }),
    });
}

function verifyExistsAdt<Output, ErrorOutput>(
    result: Result<Output | undefined, ErrorOutput>,
): Result<Output, ErrorOutput> {
    return result.map(verifyExists);
}

function verifyExists<Output>(result: Output | undefined): Output {
    if (result != undefined) return result;
    else throw Error('Response contained no body');
}

async function convertResponseToError(response: Response): Promise<ApiError> {
    const contentType = response.headers.get('content-type');
    const dateString = response.headers.get('date');
    const errorResponse: ApiErrorResponse =
        contentType && contentType.startsWith('application/json')
            ? await response.json()
            : {
                  messageRows: [await tryToReadText(response)].filter(filterNotEmpty),
                  correlationId: 'FAILED',
                  timestamp: dateString || Date(),
              };
    return {
        status: response.status,
        response: {
            ...errorResponse,
            status: response.status,
        },
    };
}

async function tryToReadText(response: Response): Promise<string | undefined> {
    try {
        return await response.text();
    } catch {
        return undefined;
    }
}

const showHttpError = (response: ApiErrorResponse) => {
    const msg =
        response.localizedMessageKey &&
        i18n.t(response.localizedMessageKey, response.localizedMessageParams);
    const content = msg || response.messageRows.map((r) => `${r}`).join('\n');
    Snackbar.error(`Request failed (${response.status})`, { body: `${content}` });
};
