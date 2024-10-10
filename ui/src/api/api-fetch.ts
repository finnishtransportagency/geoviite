import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import i18n from 'i18next';
import { err, ok, Result } from 'neverthrow';
import { filterNotEmpty } from 'utils/array-utils';
import Cookies from 'js-cookie';
import { LocalizationParams } from 'i18n/config';
import i18next from 'i18next';
import { TimeStamp } from 'common/common-model';
import { appStore } from 'store/store';
import { createDelegates } from 'store/store-utils';
import { commonActionCreators } from 'common/common-slice';

export const API_URI = '/api';

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';

function getCsrfCookie(): string | undefined {
    return Cookies.get('XSRF-TOKEN');
}

const TOKEN_EXPIRED = 'error.unauthorized.token-expired';
const INVALID_VERSION = 'error.bad-request.invalid-version';

const GEOVIITE_UI_VERSION_HEADER_KEY = 'x-geoviite-ui-version';

const geoviiteUiVersion = (): string => {
    return appStore.getState().common.version ?? '';
};

const JSON_HEADERS: HeadersInit = {
    'Accept': 'application/json',
    'Content-Type': 'application/json',
};

const createJsonHeaders = () => {
    const headers: HeadersInit = {
        ...JSON_HEADERS,
        [GEOVIITE_UI_VERSION_HEADER_KEY]: geoviiteUiVersion(),
    };

    const csrfToken = getCsrfCookie();
    return csrfToken
        ? {
              ...headers,
              'X-XSRF-TOKEN': csrfToken,
          }
        : { ...headers };
};

export type ApiError = {
    status: number;
    response: ApiErrorResponse;
};

export type ApiErrorResponse = {
    messageRows: string[];
    localizationKey: string;
    localizationParams: LocalizationParams;
    correlationId: string;
    timestamp: TimeStamp;
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

export const postFormNonNull = async <Output>(
    path: string,
    data: FormData,
    toastFailure: boolean = true,
): Promise<Output> => {
    return fetchFormNonNull(path, 'POST', data, toastFailure);
};

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

export async function putFormNonNull<Output>(
    path: string,
    data: FormData,
    toastFailure: boolean = true,
): Promise<Output> {
    return fetchFormNonNull<Output>(path, 'PUT', data, toastFailure);
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

const fetchFormNonNull = async <Output>(
    path: string,
    method: HttpMethod,
    data: FormData,
    toastFailure: boolean = true,
): Promise<Output> => {
    const result = await fetchFormNonNullAdt<Output>(path, method, data);

    if (result.isErr() && result.error && toastFailure) {
        showHttpError(path, result.error);
    }

    return result.isOk() ? result.value : Promise.reject(result.error);
};

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
        Snackbar.error(i18next.t('error.expected-non-null'));

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
        Snackbar.error(i18next.t('error.expected-non-null'));

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
        const errorIsTokenExpiry =
            response.status === 401 &&
            (response.headers.has('session-expired') ||
                errorResponse.response.localizationKey === TOKEN_EXPIRED);

        const versionMismatch =
            response.status === 400 && errorResponse.response.localizationKey === INVALID_VERSION;

        if (retryOnTokenExpired && errorIsTokenExpiry) {
            return executeBodyRequestInternal(fetchFunction, false);
        } else if (versionMismatch) {
            createDelegates(commonActionCreators).setVersionStatus('reload');
            return err(errorResponse.response);
        } else {
            if (errorIsTokenExpiry) Snackbar.sessionExpired();
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
        headers: {
            'X-XSRF-TOKEN': getCsrfCookie() || '',
            [GEOVIITE_UI_VERSION_HEADER_KEY]: geoviiteUiVersion(),
        },
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
    Snackbar.apiError(
        getLocalizedIssue(response, 'error.request-failed', { path }),
        path,
        response,
    );
};

export const getLocalizedIssue = (
    response: ApiErrorResponse | undefined,
    defaultKey: string,
    defaultParams: LocalizationParams = {},
): string => {
    const key = response?.localizationKey || defaultKey;
    const params = response?.localizationParams || defaultParams;
    return i18n.t(key, params);
};
