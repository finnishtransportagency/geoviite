import React from 'react';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { Project, ProjectId } from 'geometry/geometry-model';
import { useTranslation } from 'react-i18next';
import { getProjects } from 'geometry/geometry-api';
import { compareNamed } from 'common/common-model';

type ProjectDropdownProps = {
    id: ProjectId;
    planProject?: Project;
    setProject: (id: ProjectId) => void;
    onAddProject: (nameSuggestion: string) => void;
};

export const ProjectDropdown: React.FC<ProjectDropdownProps> = ({
    id,
    planProject,
    setProject,
    onAddProject,
}) => {
    const { t } = useTranslation();
    const [projects, projectLoaderStatus] = useLoaderWithStatus(getProjects, [id]);
    const projectsWithPlan =
        planProject && !projects?.some((p) => p.id === planProject.id)
            ? [...(projects ?? []), planProject]
            : (projects ?? []);
    return projectLoaderStatus !== LoaderStatus.Ready ? (
        <Spinner />
    ) : (
        <FieldLayout
            value={
                <Dropdown
                    wide
                    wideList
                    value={id}
                    options={projectsWithPlan
                        .map((project) => ({
                            name: project.name,
                            value: project.id,
                            qaId: `project-${project.id}`,
                        }))
                        .sort(compareNamed)}
                    onChange={(projectId) => {
                        projectId && projectId !== id && setProject(projectId);
                    }}
                    onAddClick={onAddProject}
                />
            }
            help={t('im-form.name-help')}
        />
    );
};
