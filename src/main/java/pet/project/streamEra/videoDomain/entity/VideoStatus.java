package pet.project.streamEra.videoDomain.entity;


public enum VideoStatus {
    PENDING {
        @Override
        public boolean canTransitionTo(VideoStatus target) {
            return target == PROCESSING || target == ERROR;
        }
    },
    PROCESSING {
        @Override
        public boolean canTransitionTo(VideoStatus target) {
            return target == OK || target == ERROR;
        }
    },
    OK {
        @Override
        public boolean canTransitionTo(VideoStatus target) {
            return target == DELETED;
        }
    },
    ERROR {
        @Override
        public boolean canTransitionTo(VideoStatus target) {
            return target == DELETED;
        }
    },
    DELETED {
        @Override
        public boolean canTransitionTo(VideoStatus target) {
            return false;
        }
    };

    public abstract boolean canTransitionTo(VideoStatus target);
}
