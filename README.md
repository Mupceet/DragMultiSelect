# DragMultiSelect

很多应用的列表都有多选的需求，通常流程为：

1. 进入选择模式：用户长按启用选择，或者通过按钮直接进入待选择的状态；
1. 进行选择：用户滚动列表，点击选择条目；
1. 退出选择模式：对已选择项进行操作后（如删除、分享）或者按返回键退出选择模式；

在进行选择时，如果要连续地选择则需要重复地点击，本库旨在提供该场景的最佳解决方案，通过**支持滑动连续选择以及到达列表边缘时自动滚动**的特性极大地提高连续选择的效率。

首先从连续选择的行为说起。

## 连续选择的行为

在进行连续选择时，可以将一次行为拆解成三个环节：

1. 触发选择时，手指按下时的条目（该条目称为第一条目）的状态：**状态设为选中**或者**状态反选**；
2. 触发选择后，往外滑动时经过的条目的状态：**状态与第一条目的状态一致**；
3. 触发选择后，往回滑动时经过的条目的状态：**状态不变**、**状态与第一条目的状态相反**、或者**恢复之前状态**；

根据实际的交互要求，我们组合定义出六种行为：

- SelectAndKeep：选中第一个 Item，其他 Item 在滑过时被选中，往回滑时保持选中。
- SelectAndReverse：选中第一个 Item，其他 Item 在滑过时被选中，往回滑时取消选中。
- SelectAndUndo：选中第一个 Item，其他 Item 在滑过时被选中，往回滑时恢复原状态。
- ToggleAndKeep：反选第一个 Item，其他 Item 在滑过时与第一个 Item 的状态保持一致，往回滑时保持状态不变。
- ToggleAndReverse：反选第一个 Item，其他 Item 在滑过时与第一个 Item 的状态保持一致，往回滑时与第一个 Item 的状态相反。
- ToggleAndUndo：反选第一个 Item，其他 Item 在滑过时与第一个 Item 的状态保持一致，往回滑时恢复该 Item 的原状态。

这些模式的效果请看 GIF 图：

TODO： 待补充效果图

如果开发者有明确的触发连续选择的时机例如**长按**或**触控特定控件**，可直接调用**拖动选择**的 API 让用户不抬手时完成连续选择；如果列表项有复选框可以提示用户在此区域内开始选择，则可以设置启用**滑动选择**的 API 得以在尽可能少地修改原项目代码的情况下支持用户连续选择。其中滑动选择功能指的是为列表指定一个特定区域，只要用户触摸在该区域内就可以开始进行连续选择。

下面展示的是具体使用步骤。

## Step 1 of 4: 创建选择时的回调

要正常地使用此功能必须设置选择时的回调接口，通过该回调接口可以执行选择动作的执行。接口分为 Callback 和 AdvanceCallback 两种。

### Callback

如果只需要实现最常见的 SelectAndReverse 效果的话，创建简单的 `DragMultiSelectHelper.Callback` 即可。

```java
private DragMultiSelectHelper.Callback mDragMultiSelectHelperCallback =
    new DragMultiSelectHelper.Callback() {
        @Override
        public void onSelectChange(int position, boolean isSelected) {
            // 更新该条目的状态
            mAdapter.select(position, isSelected);
        }
    };
```

如果需要在选择的开始与结束时进行处理，重写父类的方法即可。

```java
private DragMultiSelectHelper.Callback mDragMultiSelectHelperCallback =
    new DragMultiSelectHelper.Callback() {
        @Override
        public void onSelectChange(int position, boolean isSelected) {
            // 更新该条目的状态
            mAdapter.select(position, isSelected);
        }

        @Override
        public void onSelectStart(int start) {
            super.onSelectStart(start);
            // 选择从此开始
        }

        @Override
        public void onSelectEnd(int end) {
            super.onSelectEnd(end);
            // 选择在此结束
        }
    };
```

### AdvanceCallback

要使用其它几种策略，需要创建 `DragMultiSelectHelper.AdvanceCallback`。

```java
private DragMultiSelectHelper.Callback mDragSelectTouchHelperCallback =
    new AdvanceCallback<String>(AdvanceCallback.Behavior.SelectAndUndo) {
        @Override
        public Set<String> currentSelectedId() {
            return mAdapter.getSelectionSet();
        }

        @Override
        public String getItemId(int position) {
            return mAdapter.getItemInfo(position);
        }

        @Override
        public boolean updateSelectState(int position, boolean isSelected) {
            // 更新该条目的状态
            return mAdapter.select(position, isSelected);
        }
    };
```

同样的，如果需要在选择的开始与结束时进行处理，重写父类的方法即可。

## Step 2 of 4: 创建 DragMultiSelectHelper

通常情况下，如果不启用**滑动选择**的功能，使用默认的配置即可。

```java
mDragMultiSelectHelper = new DragMultiSelectHelper(mDragMultiSelectHelperCallback);
```

可以配置的选项如下代码所示，并列出了内部对应的默认值。

```java
mDragMultiSelectHelper
    .setEdgeType(DragMultiSelectHelper.EdgeType.INSIDE_EXTEND) // 设置是否允许在列表之外继续滚动，默认允许
    .setRelativeHotspotEdges(0.2f) // 默认滚动区高度为列表的 1/5
    .setMaximumHotspotEdges(Float.MAX_VALUE) // 默认滚动区高度绝对值不作限制
    .setRelativeVelocity(1.0f) // 默认最大滚动速度为每秒 100% 的列表高度
    .setMaximumVelocity(dp2px(1575)) // 默认最大滚动速度最多为每秒 1575 dp
    .setMinimumVelocity(dp2px(315)) // 默认最大滚动速度最少为每秒 315 dp
    .setAutoEnterSlideState(false) // 设置自动进入滑动选择模式，默认不允许
    .setAllowDragInSlideState(false) // 设置在滑动选择模式下允许长按拖动选择，默认不允许
    .setSlideArea(0, 0); // 滑动选择模式下指定的滑动区域 start~end
```

## Step 3 of 4: RecyclerView 关联 DragMultiSelectHelper

将上面创建好的 DragMultiSelectHelper 与 RecyclerView 关联：

```java
mDragMultiSelectHelper.attachToRecyclerView(mRecyclerView);
```

## Step 4 of 4: 启用选择模式

需要启用选择时（通常是长按时）调用 `activeDragSelect(position)` 即可进行拖动多选。又或者在合适的时候调用 `activeSlideSelect()`，直接就让列表处于滑动选择模式，列表进入滑动选择模式时，需要主动调用 `inactiveSelect()` 退出该选择模式。

```java
mDragMultiSelectHelper.activeDragSelect(position);
// or
mDragMultiSelectHelper.activeSlideSelect();
mDragMultiSelectHelper.inactiveSelect();
```

## 致谢

此库的实现参考了以下三个拖动多选的库：

- [afollestad/drag-select-recyclerview](https://github.com/afollestad/drag-select-recyclerview)
- [weidongjian/AndroidDragSelect-SimulateGooglePhoto](https://github.com/weidongjian/AndroidDragSelect-SimulateGooglePhoto)
- [MFlisar/DragSelectRecyclerView](https://github.com/MFlisar/DragSelectRecyclerView)

以及 [AutoScrollHelper.java](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:core/core/src/main/java/androidx/core/widget/AutoScrollHelper.java) 完成核心功能的开发，并且参考 [ItemTouchHelper.java](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:recyclerview/recyclerview/src/main/java/androidx/recyclerview/widget/ItemTouchHelper.java) 对代码进行接口设计与封装。

## License

```text
Copyright 2020 Mupceet

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
