<script setup lang="ts">
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';

import { editExerciseGroup, getExerciseGroupData } from '../../../../api/exercise';
import { t } from '../../../../i18n';
import { exerciseGroupManagmentRouteName } from '../../../../router/names';

const router = useRouter();

onMounted(() => {
  var exerciseId = document.getElementById('exerciseIdElement')!.innerHTML;
  var groupId = document.getElementById('groupIdElement')!.innerHTML;

  return loadFirstDataOfGroup(exerciseId, groupId);
});

async function loadFirstDataOfGroup(exerciseId: string, groupId: string): Promise<void> {
  await getExerciseGroupData(exerciseId, groupId)
    .catch(() => console.log('fail'))
    .then((groupResult) => {
      (<HTMLInputElement>document.getElementById('groupTimeId')!).value = <string>groupResult?.time;
      //(<HTMLInputElement>document.getElementById("groupMaxSizeId")!).value = <string>groupResult?.maxSize;
      (<HTMLInputElement>document.getElementById('groupLocationId')!).value = <string>groupResult?.location;
      (<HTMLInputElement>document.getElementById('groupDayId')!).value = <string>groupResult?.day;
    });
}

async function editGroup(): Promise<void> {
  var exerciseId = document.getElementById('exerciseIdElement')!.innerHTML;
  var groupId = document.getElementById('groupIdElement')!.innerHTML;

  var groupDay = (<HTMLInputElement>document.getElementById('groupDayId')!).value;
  var groupTime = (<HTMLInputElement>document.getElementById('groupTimeId')!).value;
  var groupLocation = (<HTMLInputElement>document.getElementById('groupLocationId')!).value;
  var groupMaxSize = (<HTMLInputElement>document.getElementById('groupMaxSizeId')!).value;

  if (
    exerciseId == null ||
    exerciseId == undefined ||
    exerciseId == '' ||
    groupId == null ||
    groupId == undefined ||
    groupId == '' ||
    groupDay == null ||
    groupDay == undefined ||
    groupDay == '' ||
    groupTime == null ||
    groupTime == undefined ||
    groupTime == '' ||
    groupLocation == null ||
    groupLocation == undefined ||
    groupLocation == '' ||
    groupMaxSize == null ||
    groupMaxSize == undefined ||
    groupMaxSize == ''
  ) {
    alert(t('exercise.enterCorrectInfo'));
  } else {
    await editExerciseGroup(exerciseId, groupId, groupDay, groupTime, groupLocation, groupMaxSize)
      .catch(() => console.log('fail'))
      .then((resultEditGroup) => {
        if (resultEditGroup != null && resultEditGroup.status == 'SUCCESS') {
          return router.push({ name: exerciseGroupManagmentRouteName, params: { exerciseId: exerciseId } });
        } else {
          alert(t('exercise.failProcess'));
        }
      });
  }
}
</script>

<template>
  <main class="home">
    <div id="exerciseIdElement" style="visibility: hidden">{{ $route.params.exerciseId }}</div>
    <div id="groupIdElement" style="visibility: hidden">{{ $route.params.groupId }}</div>
    <h2 v-text="t('exercise.groupTiltle') + ' ' + $route.params.exerciseId" />

    <div>
      <RouterLink :to="{ name: exerciseGroupManagmentRouteName }">{{ t('exercise.backToGroupManagement') }}</RouterLink>
    </div>

    <br />
    <h2 v-text="t('exercise.groupId') + ' ' + $route.params.groupId" />

    <br />
    <div class="custom-select">
      <form @submit.prevent="editGroup">
        <fieldset>
          <div class="form-fields">
            <label for="groupDayId" class="label-beauty"> {{ t('exercise.groupDay') }}</label>

            <select
              id="groupDayId"
              style="width: 60%; background: rgb(251, 251, 251)"
              class="form-control"
              name="registrationOpen"
            >
              <option value="MONDAY">{{ t('exercise.WeekdayMonday') }}</option>
              <option value="TUESDAY">{{ t('exercise.WeekdayTuesday') }}</option>
              <option value="WEDNESDAY">{{ t('exercise.WeekdayWednesday') }}</option>
              <option value="THURSDAY">{{ t('exercise.WeekdayThursday') }}</option>
              <option value="FRIDAY">{{ t('exercise.WeekdayFriday') }}</option>
              <option value="SATURDAY">{{ t('exercise.WeekdaySaturday') }}</option>
              <option value="SUNDAY">{{ t('exercise.WeekdaySunday') }}</option>
            </select>
            <label for="groupTimeId" class="label-beauty"> {{ t('exercise.groupTime') }}</label>
            <input id="groupTimeId" class="input-beauty" />
            <label for="groupLocationId" class="label-beauty"> {{ t('exercise.groupLocation') }}</label>
            <input id="groupLocationId" class="input-beauty" />
            <label for="groupMaxSizeId" class="label-beauty"> {{ t('exercise.groupMaxSize') }}</label>
            <input id="groupMaxSizeId" class="input-beauty" />

            <input
              id="editgroupBtnId"
              type="submit"
              class="button btn-beauty-submit"
              style="width: 15%"
              :value="t('exercise.saveGroup')"
            />
          </div>
        </fieldset>
      </form>
    </div>

    <br />
    <br />
  </main>
</template>

<style scoped>
@import '../../../../styles/new/btn-beauty-submit.css';
@import '../../../../styles/new/inputLable-beauty.css';
@import '../../../../styles/new/select-beauty.css';
</style>
