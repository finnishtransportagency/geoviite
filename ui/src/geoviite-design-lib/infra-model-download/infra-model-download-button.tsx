import * as React from 'react';
import { GeometryPlanHeader, GeometryPlanId } from 'geometry/geometry-model';
import { Button, ButtonProps } from 'vayla-design-lib/button/button';
import { DOWNLOAD_GEOMETRY } from 'user/user-model';
import { PrivilegeRequired } from 'user/privilege-required';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { inframodelDownloadUri } from 'infra-model/infra-model-api';
import { useLoader } from 'utils/react-utils';
import { getGeometryPlanHeader } from 'geometry/geometry-api';
import { ConfirmDownloadUnreliableInfraModelDialog } from 'infra-model/list/confirm-download-unreliable-infra-model-dialog';

type InfraModelDownloadProps = {
    planId?: GeometryPlanId;
    planHeader?: GeometryPlanHeader;
} & ButtonProps;

export const InfraModelDownloadButton = React.forwardRef<
    HTMLButtonElement,
    InfraModelDownloadProps
>(function InfraModelDownloadButton(
    { planId, planHeader: preLoadedPlanHeader, ...props }: InfraModelDownloadProps,
    ref,
) {
    const [isConfirmVisible, setConfirmVisible] = React.useState<boolean>(false);
    const planHeader = useLoader(
        () =>
            preLoadedPlanHeader
                ? Promise.resolve(preLoadedPlanHeader)
                : planId
                ? getGeometryPlanHeader(planId)
                : undefined,
        [planId, preLoadedPlanHeader],
    );

    return (
        <PrivilegeRequired privilege={DOWNLOAD_GEOMETRY}>
            <Button
                icon={Icons.Download}
                onClick={() => {
                    if (planHeader !== undefined) {
                        if (planHeader.source === 'PAIKANNUSPALVELU') {
                            setConfirmVisible(true);
                        } else {
                            location.href = inframodelDownloadUri(planHeader.id);
                        }
                    }
                }}
                {...props}
                ref={ref}></Button>
            {isConfirmVisible && planHeader && (
                <ConfirmDownloadUnreliableInfraModelDialog
                    onClose={() => setConfirmVisible(false)}
                    plan={planHeader}
                />
            )}
        </PrivilegeRequired>
    );
});
