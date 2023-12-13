import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import i18n from 'i18next';
import { err, ok, Result } from 'neverthrow';
import { filterNotEmpty } from 'utils/array-utils';
import Cookies from 'js-cookie';
import { LocalizationParams } from 'i18n/config';

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

export type ApiError = {
    status: number;
    response: ApiErrorResponse;
};

export type ApiErrorResponse = {
    messageRows: string[];
    correlationId: string;
    timestamp: string;
    localizedMessageKey?: string;
    localizedMessageParams: LocalizationParams;
    status: number;
};

export type Page<T> = {
    totalCount: number;
    items: T[];
    start: number;
};

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

const wrapApiErrorResponse = Symbol('wrap api error response');
type WrappedApiErrorResponse = { [k in typeof wrapApiErrorResponse]: ApiErrorResponse };
function isWrappedApiError(x: unknown): x is WrappedApiErrorResponse {
    return typeof x === 'object' && x !== null && wrapApiErrorResponse in x;
}

export function getNonNull<Output>(path: string, toastFailure: boolean = true): Promise<Output> {
    return fetchNonNull(path, 'GET', undefined, toastFailure);
}

export function getNullable<Output>(
    path: string,
    toastFailure: boolean = true,
): Promise<Output | undefined> {
    return fetchNullable(path, 'GET', undefined, toastFailure);
}

export function getNonNullAdt<Output>(
    path: string,
): Promise<Result<Output, ApiErrorResponse | undefined>> {
    return fetchNonNullAdt(path, 'GET', undefined);
}

export function getNullableAdt<Output>(
    path: string,
): Promise<Result<Output | undefined, ApiErrorResponse>> {
    return fetchNullableAdt(path, 'GET', undefined);
}

export async function postNullable<Input, Output>(
    path: string,
    body: Input,
    toastFailure: boolean = true,
): Promise<Output | undefined> {
    return fetchNullable(path, 'POST', body, toastFailure);
}

export async function postNonNull<Input, Output>(
    path: string,
    body: Input,
    toastFailure: boolean = true,
): Promise<Output> {
    return fetchNonNull(path, 'POST', body, toastFailure);
}

export async function postNonNullAdt<Input, Output>(
    path: string,
    body: Input,
): Promise<Result<Output, ApiErrorResponse | undefined>> {
    return fetchNonNullAdt<Input, Output>(path, 'POST', body);
}

export async function postNullableAdt<Input, Output>(
    path: string,
    body: Input,
): Promise<Result<Output | undefined, ApiErrorResponse>> {
    return fetchNullableAdt<Input, Output>(path, 'POST', body);
}

export async function postFormNonNullAdt<Output>(
    path: string,
    data: FormData,
): Promise<Result<Output, ApiErrorResponse | undefined>> {
    return fetchFormNonNullAdt<Output>(path, 'POST', data);
}

export async function putNullable<Input, Output>(
    path: string,
    body: Input,
    toastFailure: boolean = true,
): Promise<Output | undefined> {
    return fetchNullable(path, 'PUT', body, toastFailure);
}

export async function putNonNull<Input, Output>(
    path: string,
    body: Input,
    toastFailure: boolean = true,
): Promise<Output> {
    return fetchNonNull(path, 'PUT', body, toastFailure);
}

export async function putNonNullAdt<Input, Output>(
    path: string,
    body: Input,
): Promise<Result<Output, ApiErrorResponse | undefined>> {
    return fetchNonNullAdt<Input, Output>(path, 'PUT', body);
}

export async function putNullableAdt<Input, Output>(
    path: string,
    body: Input,
): Promise<Result<Output | undefined, ApiErrorResponse>> {
    return fetchNullableAdt<Input, Output>(path, 'PUT', body);
}

export async function putFormNonNullAdt<Output>(
    path: string,
    data: FormData,
): Promise<Result<Output, ApiErrorResponse | undefined>> {
    return fetchFormNonNullAdt<Output>(path, 'PUT', data);
}

export async function deleteNullable<Output>(
    path: string,
    toastFailure: boolean = true,
): Promise<Output | undefined> {
    return fetchNullable(path, 'DELETE', undefined, toastFailure);
}

export async function deleteNonNull<Output>(
    path: string,
    toastFailure: boolean = true,
): Promise<Output> {
    return fetchNonNull(path, 'DELETE', undefined, toastFailure);
}

export async function deleteNonNullAdt<Input, Output>(
    path: string,
    body: Input,
): Promise<Result<Output, ApiErrorResponse | undefined>> {
    return fetchNonNullAdt(path, 'DELETE', body);
}

export async function deleteNullableAdt<Output>(
    path: string,
): Promise<Result<Output | undefined, ApiErrorResponse>> {
    return fetchNullableAdt(path, 'DELETE', undefined);
}

async function fetchNullable<Input, Output>(
    path: string,
    method: HttpMethod,
    body: Input,
    toastFailure: boolean = true,
): Promise<Output | undefined> {
    const result = await fetchNullableAdt<Input, Output>(path, method, body);

    if (result.isErr() && toastFailure) {
        showHttpError(path, result.error);
    }

    return result.isOk() ? result.value : Promise.reject(result.error);
}

async function fetchNonNull<Input, Output>(
    path: string,
    method: HttpMethod,
    body: Input,
    toastFailure: boolean = true,
): Promise<Output> {
    const result = await fetchNonNullAdt<Input, Output>(path, method, body);

    if (result.isErr() && result.error && toastFailure) {
        showHttpError(path, result.error);
    }

    return result.isOk() ? result.value : Promise.reject(result.error);
}

async function fetchNullableAdt<Input, Output>(
    path: string,
    method: HttpMethod,
    body: Input,
): Promise<Result<Output | undefined, ApiErrorResponse>> {
    const r = await executeRequest<Input, Output, WrappedApiErrorResponse>(
        path,
        body,
        (e) => {
            return { [wrapApiErrorResponse]: e };
        },
        method,
    );

    return isWrappedApiError(r) ? err(r[wrapApiErrorResponse]) : ok(r);
}

async function fetchNonNullAdt<Input, Output>(
    path: string,
    method: HttpMethod,
    body: Input,
): Promise<Result<Output, ApiErrorResponse | undefined>> {
    const result = await fetchNullableAdt<Input, Output>(path, method, body);

    if (result.isOk() && result.value === undefined) {
        Snackbar.error('Expected non-null result but got null');

        return err(undefined);
    } else {
        return result as Result<Output, ApiErrorResponse>;
    }
}

export async function fetchFormNonNullAdt<Output>(
    path: string,
    method: HttpMethod,
    data: FormData,
): Promise<Result<Output, ApiErrorResponse | undefined>> {
    const r = await executeBodyRequestInternal<Output>(
        () => getFormResponse(path, data, method),
        true,
    );

    if (r.isOk() && r.value === undefined) {
        Snackbar.error('Expected non-null result but got null');

        return err(undefined);
    } else {
        return r as Result<Output, ApiErrorResponse>;
    }
}

async function executeRequest<Input, Output, ErrorOutput>(
    path: string,
    data: Input | undefined,
    errorHandler: (response: ApiErrorResponse) => ErrorOutput,
    method: HttpMethod,
): Promise<Output | ErrorOutput | undefined> {
    const result: Result<Output | undefined, ApiErrorResponse> = await executeBodyRequestInternal(
        () => getJsonResponse(path, data, method),
        true,
    );
    if (result.isOk()) return result.value;
    else return errorHandler(result.error);
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

async function getJsonResponse<Input>(
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

const showHttpError = (path: string, response: ApiErrorResponse) => {
    const msg =
        response.localizedMessageKey &&
        i18n.t(response.localizedMessageKey, response.localizedMessageParams);

    const content = msg || response.messageRows.map((r) => `${r}`).join('\n');

    Snackbar.error(i18n.t('error.request-failed', { path }), content);
};
