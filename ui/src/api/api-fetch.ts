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
    status: number;
};

export type Page<T> = {
    totalCount: number;
    items: T[];
    start: number;
};

const throwErrorHandler = (response: ApiErrorResponse) => {
    throw response;
};

const ignoreErrorHandler = defaultValueErrorHandler(null);

function defaultValueErrorHandler<T>(defaultValue: T): ErrorHandler<T> {
    return (response: ApiErrorResponse): T => {
        showHttpError(response);
        return defaultValue;
    };
}

export function queryParams(params: Record<string, unknown>): string {
    const nonNull = Object.keys(params)
        .map((key) => {
            const value = params[key];
            return value != null ? `${key}=${value}` : null;
        })
        .filter((p) => p != null);
    return nonNull.length == 0 ? '' : `?${nonNull.join('&')}`;
}

/**
 * @deprecated Throwing loses type information. If you need to handle the error in your use-place, use getAdt instead, fetching a result object with output or error
 */
export async function getThrowError<Output>(path: string): Promise<Output> {
    return executeRequest<undefined, Output, Output>(
        path,
        undefined,
        throwErrorHandler,
        'GET',
    ).then(verifyNonNull);
}

export async function getWithDefault<Output>(path: string, defaultValue: Output): Promise<Output> {
    return executeRequest<undefined, Output, Output>(
        path,
        undefined,
        defaultValueErrorHandler(defaultValue),
        'GET',
    ).then((val) => (val != null ? val : defaultValue));
}

export async function getIgnoreError<Output>(path: string): Promise<Output | null> {
    return executeRequest<undefined, Output, null>(path, undefined, ignoreErrorHandler, 'GET');
}

export async function postIgnoreError<Input, Output>(
    path: string,
    data: Input,
): Promise<Output | null> {
    return executeRequest<Input, Output, null>(path, data, ignoreErrorHandler, 'POST');
}

export async function putIgnoreError<Input, Output>(
    path: string,
    data: Input,
): Promise<Output | null> {
    return executeRequest<Input, Output, null>(path, data, ignoreErrorHandler, 'PUT');
}

export async function deleteIgnoreError<Output>(path: string): Promise<Output | null> {
    return executeRequest<undefined, Output, null>(path, undefined, ignoreErrorHandler, 'DELETE');
}

// Result object returning versions of HTTP methods (ADT)
export async function getAdt<Output>(
    path: string,
    showErrorMessage = false,
): Promise<Result<Output, ApiErrorResponse>> {
    return await executeBodyRequestAdt<undefined, Output>(
        path,
        undefined,
        'GET',
        showErrorMessage,
    ).then(verifyNonNullAdt);
}

export async function postAdt<Input, Output>(
    path: string,
    data: Input,
    showErrorMessage = false,
): Promise<Result<Output, ApiErrorResponse>> {
    return await executeBodyRequestAdt<Input, Output>(path, data, 'POST', showErrorMessage).then(
        verifyNonNullAdt,
    );
}

export async function putAdt<Input, Output>(
    path: string,
    data: Input,
    showErrorMessage = false,
): Promise<Result<Output, ApiErrorResponse>> {
    return await executeBodyRequestAdt<Input, Output>(path, data, 'PUT', showErrorMessage).then(
        verifyNonNullAdt,
    );
}

export async function deleteAdt<Input, Output>(
    path: string,
    data: Input,
    showErrorMessage = false,
): Promise<Result<Output, ApiErrorResponse>> {
    return await executeBodyRequestAdt<Input, Output>(path, data, 'DELETE', showErrorMessage).then(
        verifyNonNullAdt,
    );
}

export async function postFormIgnoreError<Output>(
    path: string,
    data: FormData,
): Promise<Output | null> {
    return postFormWithError<Output, null>(path, data, ignoreErrorHandler);
}

export async function postFormWithError<Output, ErrorOutput>(
    path: string,
    data: FormData,
    errorHandler: ErrorHandler<ErrorOutput>,
): Promise<Output | ErrorOutput> {
    const result: Result<Output | null, ApiErrorResponse> = await executeBodyRequestInternal(
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
): Promise<Output | null> {
    const result: Result<Output | null, ApiErrorResponse> = await executeBodyRequestInternal(
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
): Promise<Output | ErrorOutput | null> {
    const result: Result<Output | null, ApiErrorResponse> = await executeBodyRequestInternal(
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
): Promise<Result<Output | null, ApiErrorResponse>> {
    const result: Result<Output | null, ApiErrorResponse> = await executeBodyRequestInternal(
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
): Promise<Result<Output | null, ApiErrorResponse>> {
    const response = await fetchFunction();

    if (response.status === 204) {
        return ok(null);
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
            if (response.status === 401 && response.headers.has('session-expired')) Snackbar.sessionExpired();
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

function verifyNonNullAdt<Output, ErrorOutput>(
    result: Result<Output | null, ErrorOutput>,
): Result<Output, ErrorOutput> {
    return result.map(verifyNonNull);
}

function verifyNonNull<Output>(result: Output | null): Output {
    if (result != null) return result;
    else throw Error('Response contained no body');
}

async function convertResponseToError(response: Response): Promise<ApiError> {
    const contentType = response.headers.get('content-type');
    const dateString = response.headers.get('date');
    const errorResponse: ApiErrorResponse =
        contentType === 'application/json'
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
    const msg = response.localizedMessageKey && i18n.t(response.localizedMessageKey);
    const content = msg || response.messageRows.map((r) => `${r}`).join('\n');
    Snackbar.error(`Request failed (${response.status})`, `${content}`);
};
